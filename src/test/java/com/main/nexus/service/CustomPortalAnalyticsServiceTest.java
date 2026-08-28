package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.AdminCustomPortalAnalyticsDTO;
import com.main.nexus.dto.CustomPortalAnalyticsDTO;
import com.main.nexus.dto.TrackPortalEventDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.CustomPortalVisitEvent;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CustomPortalEventType;
import com.main.nexus.model.enums.CustomPortalRequestStatus;
import com.main.nexus.model.enums.CustomPortalStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalRequestRepository;
import com.main.nexus.repository.CustomPortalVisitEventRepository;
import com.main.nexus.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomPortalAnalyticsServiceTest {

    @Mock private CustomPortalVisitEventRepository eventRepository;
    @Mock private CustomPortalRepository customPortalRepository;
    @Mock private CustomPortalRequestRepository customPortalRequestRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ProjectRepository projectRepository;

    @InjectMocks private CustomPortalAnalyticsService service;

    private CustomPortal portal;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(5L);
        Company company = new Company();
        company.setId(1L);
        company.setUser(user);

        portal = new CustomPortal();
        portal.setId(2L);
        portal.setCompany(company);
        portal.setStatus(CustomPortalStatus.ACTIVE);
        portal.setSubdomain("acme");

        when(companyRepository.findByUserId(5L)).thenReturn(Optional.of(company));
        when(customPortalRepository.findByCompanyId(1L)).thenReturn(Optional.of(portal));
        when(customPortalRepository.findBySubdomainIgnoreCase("acme")).thenReturn(Optional.of(portal));
    }

    private TrackPortalEventDTO ev(CustomPortalEventType type, Integer dur) {
        return new TrackPortalEventDTO("visitor-1", type, "/", null, dur, "google.com");
    }

    @Test
    void recordEvent_noopWhenSubdomainUnknown() {
        when(customPortalRepository.findBySubdomainIgnoreCase("nope")).thenReturn(Optional.empty());
        service.recordEvent("nope", ev(CustomPortalEventType.PAGE_VIEW, null));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void recordEvent_noopWhenPortalNotActive() {
        portal.setStatus(CustomPortalStatus.SUSPENDED);
        service.recordEvent("acme", ev(CustomPortalEventType.PAGE_VIEW, null));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void recordEvent_noopWhenInvalidPayload() {
        service.recordEvent("acme", new TrackPortalEventDTO(" ", null, "/", null, null, null));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void recordEvent_persistsAndClampsDuration() {
        service.recordEvent("acme", ev(CustomPortalEventType.SESSION_END, 999_999));

        ArgumentCaptor<CustomPortalVisitEvent> cap =
                ArgumentCaptor.forClass(CustomPortalVisitEvent.class);
        verify(eventRepository).save(cap.capture());
        CustomPortalVisitEvent saved = cap.getValue();
        assertEquals(portal, saved.getCustomPortal());
        assertEquals(CustomPortalEventType.SESSION_END, saved.getType());
        assertEquals(7200, saved.getDurationSeconds()); // 2h cap
        assertEquals("google.com", saved.getReferrerHost());
    }

    @Test
    void getAnalytics_computesConversionAndFillsDayGaps() {
        when(eventRepository.countByType(eq(2L), eq(CustomPortalEventType.PAGE_VIEW), any()))
                .thenReturn(200L);
        when(eventRepository.countByType(eq(2L), eq(CustomPortalEventType.APPLY_CLICK), any()))
                .thenReturn(15L);
        when(eventRepository.countDistinctVisitors(eq(2L), any(), any())).thenReturn(120L);
        when(eventRepository.avgSessionSeconds(eq(2L), any())).thenReturn(83.6);
        // só 1 dos 7 dias tem acesso
        when(eventRepository.viewsPerDay(eq(2L), any())).thenReturn(List.<Object[]>of(
                new Object[] { java.sql.Date.valueOf(LocalDate.now()), 42L }));
        when(eventRepository.topOpportunities(eq(2L), any())).thenReturn(List.<Object[]>of(
                new Object[] { 28L, 30L }, new Object[] { 33L, 12L }));
        when(eventRepository.referrerBreakdown(eq(2L), any())).thenReturn(List.<Object[]>of(
                new Object[] { "google.com", 90L }, new Object[] { null, 60L }));

        Project p28 = new Project();
        p28.setTitle("Dev Full Stack");
        when(projectRepository.findById(28L)).thenReturn(Optional.of(p28));
        when(projectRepository.findById(33L)).thenReturn(Optional.empty());

        CustomPortalAnalyticsDTO dto = service.getAnalyticsForUser(5L, 7);

        assertEquals(7, dto.rangeDays());
        assertEquals(200L, dto.totalViews());
        assertEquals(120L, dto.uniqueVisitors());
        assertEquals(15L, dto.applyClicks());
        assertEquals(7.5, dto.conversionRate()); // 15/200*100
        assertEquals(84.0, dto.avgSessionSeconds()); // arredondado
        assertEquals(7, dto.viewsPerDay().size()); // gaps preenchidos
        assertEquals(42L, dto.viewsPerDay().get(6).views()); // hoje
        assertEquals(0L, dto.viewsPerDay().get(0).views()); // 6 dias atrás
        assertEquals("Dev Full Stack", dto.topOpportunities().get(0).title());
        assertEquals("Vaga #33", dto.topOpportunities().get(1).title()); // fallback
        assertEquals("Direto", dto.referrers().get(1).label()); // referrer null
    }

    @Test
    void getAnalytics_404WhenNoPortal() {
        when(customPortalRepository.findByCompanyId(1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getAnalyticsForUser(5L, 30));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getAnalytics_clampsRange() {
        when(eventRepository.countByType(anyLong(), any(), any())).thenReturn(0L);
        when(eventRepository.countDistinctVisitors(anyLong(), any(), any())).thenReturn(0L);
        when(eventRepository.viewsPerDay(anyLong(), any())).thenReturn(List.of());
        when(eventRepository.topOpportunities(anyLong(), any())).thenReturn(List.of());
        when(eventRepository.referrerBreakdown(anyLong(), any())).thenReturn(List.of());

        assertEquals(365, service.getAnalyticsForUser(5L, 9999).rangeDays());
        assertEquals(1, service.getAnalyticsForUser(5L, 0).rangeDays());
        assertTrue(service.getAnalyticsForUser(5L, 30).viewsPerDay().size() == 30);
    }

    @Test
    void getAnalytics_zeroConversionWhenNoViews() {
        when(eventRepository.countByType(eq(2L), eq(CustomPortalEventType.PAGE_VIEW), any()))
                .thenReturn(0L);
        when(eventRepository.countByType(eq(2L), eq(CustomPortalEventType.APPLY_CLICK), any()))
                .thenReturn(0L);
        when(eventRepository.countDistinctVisitors(eq(2L), any(), any())).thenReturn(0L);
        when(eventRepository.avgSessionSeconds(eq(2L), any())).thenReturn(null);
        when(eventRepository.viewsPerDay(eq(2L), any())).thenReturn(List.of());
        when(eventRepository.topOpportunities(eq(2L), any())).thenReturn(List.of());
        when(eventRepository.referrerBreakdown(eq(2L), any())).thenReturn(List.of());

        CustomPortalAnalyticsDTO dto = service.getAnalyticsForUser(5L, 30);
        assertEquals(0.0, dto.conversionRate());
        assertEquals(0.0, dto.avgSessionSeconds());
    }

    // ── Admin: uma plataforma por id ───────────────────────────────

    @Test
    void getAnalyticsForPortal_resolvesByIdAndAggregates() {
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(portal));
        when(eventRepository.countByType(eq(2L), eq(CustomPortalEventType.PAGE_VIEW), any()))
                .thenReturn(50L);
        when(eventRepository.countByType(eq(2L), eq(CustomPortalEventType.APPLY_CLICK), any()))
                .thenReturn(5L);
        when(eventRepository.countDistinctVisitors(eq(2L), any(), any())).thenReturn(40L);
        when(eventRepository.avgSessionSeconds(eq(2L), any())).thenReturn(60.0);
        when(eventRepository.viewsPerDay(eq(2L), any())).thenReturn(List.of());
        when(eventRepository.topOpportunities(eq(2L), any())).thenReturn(List.of());
        when(eventRepository.referrerBreakdown(eq(2L), any())).thenReturn(List.of());

        CustomPortalAnalyticsDTO dto = service.getAnalyticsForPortal(2L, 30);

        assertEquals(50L, dto.totalViews());
        assertEquals(10.0, dto.conversionRate()); // 5/50*100
        assertEquals(30, dto.viewsPerDay().size());
    }

    @Test
    void getAnalyticsForPortal_404WhenMissing() {
        when(customPortalRepository.findById(99L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getAnalyticsForPortal(99L, 30));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ── Admin: dashboard geral do módulo ──────────────────────────

    @Test
    void getSystemAnalytics_aggregatesModuleWideMetrics() {
        CustomPortal a = sysPortal(2L, company(10L, "Acme"), "acme",
                CustomPortalStatus.ACTIVE, "Pro", new BigDecimal("199.90"),
                LocalDateTime.now());
        CustomPortal b = sysPortal(3L, company(11L, "Beta"), "beta",
                CustomPortalStatus.SUSPENDED, "Pro", new BigDecimal("99.90"),
                LocalDateTime.now().minusMonths(1));
        CustomPortal c = sysPortal(4L, company(12L, "Gama"), "gama",
                CustomPortalStatus.CANCELED, "Basic", new BigDecimal("49.90"),
                LocalDateTime.now().minusMonths(10));

        when(customPortalRepository.findAll()).thenReturn(List.of(a, b, c));
        when(customPortalRequestRepository.countByStatus(CustomPortalRequestStatus.PENDING))
                .thenReturn(3L);

        when(eventRepository.countByTypeAllPortals(eq(CustomPortalEventType.PAGE_VIEW), any()))
                .thenReturn(500L);
        when(eventRepository.countByTypeAllPortals(eq(CustomPortalEventType.APPLY_CLICK), any()))
                .thenReturn(25L);
        when(eventRepository.countDistinctVisitorsAllPortals(
                eq(CustomPortalEventType.PAGE_VIEW), any())).thenReturn(300L);
        when(eventRepository.avgSessionSecondsAllPortals(any())).thenReturn(100.0);
        when(eventRepository.viewsPerDayAllPortals(any())).thenReturn(List.of());
        when(eventRepository.topOpportunitiesAllPortals(any())).thenReturn(List.of());
        when(eventRepository.referrerBreakdownAllPortals(any())).thenReturn(List.of());
        when(eventRepository.viewsPerPortal(any())).thenReturn(List.<Object[]>of(
                new Object[] { 2L, 400L }, new Object[] { 3L, 100L }));
        when(eventRepository.applyClicksPerPortal(any())).thenReturn(List.<Object[]>of(
                new Object[] { 2L, 20L }));

        AdminCustomPortalAnalyticsDTO dto = service.getSystemAnalytics(30);

        assertEquals(3L, dto.totalPortals());
        assertEquals(1L, dto.activePortals());
        assertEquals(1L, dto.suspendedPortals());
        assertEquals(1L, dto.canceledPortals());
        assertEquals(3L, dto.pendingRequests());
        assertEquals(0, new BigDecimal("199.90").compareTo(dto.monthlyRecurringRevenue()));

        // engajamento agregado
        assertEquals(500L, dto.system().totalViews());
        assertEquals(5.0, dto.system().conversionRate()); // 25/500*100

        // ranking: só plataformas com acesso, ordenado por views desc
        assertEquals(2, dto.topPortals().size());
        assertEquals("acme", dto.topPortals().get(0).subdomain());
        assertEquals(400L, dto.topPortals().get(0).views());
        assertEquals(5.0, dto.topPortals().get(0).conversionRate()); // 20/400*100
        assertEquals("beta", dto.topPortals().get(1).subdomain());
        assertEquals(0.0, dto.topPortals().get(1).conversionRate());

        // planos por contagem desc
        assertEquals("Pro", dto.portalsByPlan().get(0).planName());
        assertEquals(2L, dto.portalsByPlan().get(0).count());

        // 3 status sempre presentes
        assertEquals(3, dto.portalsByStatus().size());

        // crescimento: janela de 6 meses -> A (mês atual) e B (-1); C (-10) fora
        assertEquals(6, dto.portalsCreatedPerMonth().size());
        long created = dto.portalsCreatedPerMonth().stream()
                .mapToLong(AdminCustomPortalAnalyticsDTO.MonthlyCount::count).sum();
        assertEquals(2L, created);
    }

    @Test
    void getSystemAnalytics_emptyModule() {
        when(customPortalRepository.findAll()).thenReturn(List.of());
        when(customPortalRequestRepository.countByStatus(any())).thenReturn(0L);
        when(eventRepository.countByTypeAllPortals(any(), any())).thenReturn(0L);
        when(eventRepository.countDistinctVisitorsAllPortals(any(), any())).thenReturn(0L);
        when(eventRepository.avgSessionSecondsAllPortals(any())).thenReturn(null);
        when(eventRepository.viewsPerDayAllPortals(any())).thenReturn(List.of());
        when(eventRepository.topOpportunitiesAllPortals(any())).thenReturn(List.of());
        when(eventRepository.referrerBreakdownAllPortals(any())).thenReturn(List.of());
        when(eventRepository.viewsPerPortal(any())).thenReturn(List.of());
        when(eventRepository.applyClicksPerPortal(any())).thenReturn(List.of());

        AdminCustomPortalAnalyticsDTO dto = service.getSystemAnalytics(9999);

        assertEquals(0L, dto.totalPortals());
        assertEquals(365, dto.rangeDays()); // clamp
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.monthlyRecurringRevenue()));
        assertTrue(dto.topPortals().isEmpty());
        assertEquals(0L, dto.system().totalViews());
        assertEquals(365, dto.system().viewsPerDay().size());
    }

    private Company company(Long id, String name) {
        Company c = new Company();
        c.setId(id);
        c.setCompanyName(name);
        return c;
    }

    private CustomPortal sysPortal(Long id, Company company, String subdomain,
            CustomPortalStatus status, String plan, BigDecimal price,
            LocalDateTime createdAt) {
        CustomPortal p = new CustomPortal();
        p.setId(id);
        p.setCompany(company);
        p.setSubdomain(subdomain);
        p.setStatus(status);
        p.setPlanName(plan);
        p.setPlanPrice(price);
        p.setCreatedAt(createdAt);
        return p;
    }
}
