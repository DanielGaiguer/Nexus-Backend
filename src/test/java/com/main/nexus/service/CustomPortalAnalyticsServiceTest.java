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

import com.main.nexus.dto.CustomPortalAnalyticsDTO;
import com.main.nexus.dto.TrackPortalEventDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.CustomPortalVisitEvent;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CustomPortalEventType;
import com.main.nexus.model.enums.CustomPortalStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalVisitEventRepository;
import com.main.nexus.repository.ProjectRepository;
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
}
