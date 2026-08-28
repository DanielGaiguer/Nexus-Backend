package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.ApproveCustomPortalRequestDTO;
import com.main.nexus.dto.CreateCustomPortalDTO;
import com.main.nexus.dto.CustomPortalDTO;
import com.main.nexus.dto.CustomPortalRequestDTO;
import com.main.nexus.dto.CustomPortalSectionDTO;
import com.main.nexus.dto.UpdateCustomPortalBrandingDTO;
import com.main.nexus.dto.UpdateCustomPortalSubscriptionDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.CustomPortalRequest;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.BrandingImageKind;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import com.main.nexus.model.enums.CustomPortalRequestStatus;
import com.main.nexus.model.enums.CustomPortalStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalRequestRepository;
import com.main.nexus.repository.CustomPortalStatusHistoryRepository;
import com.main.nexus.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // muitas stubs de helper compartilhadas entre casos
class CustomPortalServiceTest {

    @Mock private CustomPortalRepository customPortalRepository;
    @Mock private CustomPortalRequestRepository requestRepository;
    @Mock private CustomPortalStatusHistoryRepository historyRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private EmailTemplateService emailTemplate;
    @Mock private SupabaseStorageService supabaseStorageService;

    @InjectMocks private CustomPortalService service;

    private User companyUser;
    private User admin;
    private Company company;

    @BeforeEach
    void setUp() {
        companyUser = new User();
        companyUser.setId(5L);
        companyUser.setEmail("acme@example.com");
        companyUser.setType(UserType.COMPANY);

        admin = new User();
        admin.setId(99L);
        admin.setEmail("admin@example.com");
        admin.setType(UserType.ADMIN);

        company = new Company();
        company.setId(1L);
        company.setUser(companyUser);
        company.setCompanyName("Acme");
        company.setStatus(CompanyStatus.APPROVED);

        when(customPortalRepository.save(any(CustomPortal.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any(CustomPortalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(companyRepository.findByUserId(5L)).thenReturn(Optional.of(company));
        when(userRepository.findByType(UserType.ADMIN)).thenReturn(List.of(admin));
    }

    private ApproveCustomPortalRequestDTO approval(String subdomain) {
        return new ApproveCustomPortalRequestDTO(
                subdomain, "Plano Pro", new BigDecimal("199.90"),
                LocalDate.now(), LocalDate.now().plusMonths(1), null);
    }

    private CustomPortalRequest pendingRequest() {
        CustomPortalRequest r = new CustomPortalRequest();
        r.setId(10L);
        r.setCompany(company);
        r.setStatus(CustomPortalRequestStatus.PENDING);
        return r;
    }

    private CustomPortal portal(CustomPortalStatus status) {
        CustomPortal p = new CustomPortal();
        p.setId(2L);
        p.setCompany(company);
        p.setStatus(status);
        p.setSubdomain("acme");
        p.setPlanName("Plano Pro");
        p.setPlanPrice(new BigDecimal("199.90"));
        p.setSubscriptionStartDate(LocalDate.now().minusMonths(1));
        p.setNextDueDate(LocalDate.now().plusDays(3));
        p.setPaymentStatus(CustomPortalPaymentStatus.UP_TO_DATE);
        return p;
    }

    // ── createRequest ────────────────────────────────────────────────

    @Test
    void createRequest_persistsPendingAndNotifiesAdmins() {
        when(customPortalRepository.existsByCompanyId(1L)).thenReturn(false);
        when(requestRepository.existsByCompanyIdAndStatus(1L, CustomPortalRequestStatus.PENDING))
                .thenReturn(false);

        CustomPortalRequestDTO dto = service.createRequest(5L, "  gostaria muito  ");

        assertEquals("PENDING", dto.status());
        verify(requestRepository).save(any(CustomPortalRequest.class));
        verify(notificationService).notifyCustomPortalRequestReceived(admin, "Acme");
    }

    @Test
    void createRequest_rejectsSecondPendingRequest() {
        when(requestRepository.existsByCompanyIdAndStatus(1L, CustomPortalRequestStatus.PENDING))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createRequest(5L, null));
        assertEquals(409, ex.getStatusCode().value());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequest_rejectsWhenPortalAlreadyExists() {
        when(customPortalRepository.existsByCompanyId(1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createRequest(5L, null));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── approveRequest ──────────────────────────────────────────────

    @Test
    void approveRequest_createsActivePortalAndMarksRequestApproved() {
        CustomPortalRequest request = pendingRequest();
        when(requestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(customPortalRepository.existsByCompanyId(1L)).thenReturn(false);
        when(customPortalRepository.existsBySubdomainIgnoreCase("acme")).thenReturn(false);

        CustomPortalDTO dto = service.approveRequest(99L, 10L, approval("Acme"));

        assertEquals("ACTIVE", dto.status());
        assertEquals("acme", dto.subdomain()); // normalizado para minúsculas
        assertTrue(dto.createdFromRequest());
        assertEquals(CustomPortalRequestStatus.APPROVED, request.getStatus());
        assertEquals(admin, request.getReviewedBy());
        verify(historyRepository).save(any());
        verify(notificationService).notifyCustomPortalRequestApproved(companyUser, "acme");
    }

    @Test
    void approveRequest_rejectsWhenRequestNotPending() {
        CustomPortalRequest request = pendingRequest();
        request.setStatus(CustomPortalRequestStatus.APPROVED);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveRequest(99L, 10L, approval("acme")));
        assertEquals(409, ex.getStatusCode().value());
        verify(customPortalRepository, never()).save(any());
    }

    @Test
    void approveRequest_rejectsInvalidSubdomain() {
        when(requestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveRequest(99L, 10L, approval("ab"))); // curto demais
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void approveRequest_rejectsReservedSubdomain() {
        when(requestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveRequest(99L, 10L, approval("www")));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void approveRequest_rejectsDuplicateSubdomain() {
        when(requestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest()));
        when(customPortalRepository.existsBySubdomainIgnoreCase("acme")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveRequest(99L, 10L, approval("acme")));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── rejectRequest ──────────────────────────────────────────────

    @Test
    void rejectRequest_requiresReason() {
        when(requestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rejectRequest(99L, 10L, "   "));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rejectRequest_marksRejectedWithReason() {
        CustomPortalRequest request = pendingRequest();
        when(requestRepository.findById(10L)).thenReturn(Optional.of(request));

        service.rejectRequest(99L, 10L, "fora do perfil comercial");

        assertEquals(CustomPortalRequestStatus.REJECTED, request.getStatus());
        assertEquals("fora do perfil comercial", request.getDecisionReason());
        verify(notificationService).notifyCustomPortalRequestRejected(companyUser, "fora do perfil comercial");
    }

    // ── criação direta ─────────────────────────────────────────────

    @Test
    void createPortalDirectly_rejectsNonApprovedCompany() {
        company.setStatus(CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

        CreateCustomPortalDTO body = new CreateCustomPortalDTO(
                1L, "acme", "Plano Pro", new BigDecimal("100"),
                LocalDate.now(), LocalDate.now().plusMonths(1), null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createPortalDirectly(99L, body));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void createPortalDirectly_buildsPortalWithoutOriginRequest() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(customPortalRepository.existsByCompanyId(1L)).thenReturn(false);
        when(customPortalRepository.existsBySubdomainIgnoreCase("acme")).thenReturn(false);

        CreateCustomPortalDTO body = new CreateCustomPortalDTO(
                1L, "ACME", "Plano Pro", new BigDecimal("100"),
                LocalDate.now(), LocalDate.now().plusMonths(1),
                CustomPortalPaymentStatus.UP_TO_DATE);

        CustomPortalDTO dto = service.createPortalDirectly(99L, body);

        assertEquals("ACTIVE", dto.status());
        assertEquals("acme", dto.subdomain());
        assertTrue(!dto.createdFromRequest());
    }

    // ── ciclo de vida ──────────────────────────────────────────────

    @Test
    void suspend_onlyFromActive() {
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(portal(CustomPortalStatus.SUSPENDED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.suspend(99L, 2L, "inadimplência"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void suspend_movesActiveToSuspendedAndRecordsHistory() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        CustomPortalDTO dto = service.suspend(99L, 2L, "inadimplência");

        assertEquals("SUSPENDED", dto.status());
        assertEquals(CustomPortalStatus.SUSPENDED, p.getStatus());
        verify(historyRepository).save(any());
        verify(notificationService).notifyCustomPortalSuspended(companyUser, "inadimplência");
    }

    @Test
    void reactivate_onlyFromSuspended() {
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(portal(CustomPortalStatus.ACTIVE)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reactivate(99L, 2L, null));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void cancel_rejectsAlreadyCanceled() {
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(portal(CustomPortalStatus.CANCELED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancel(99L, 2L, null));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void cancel_setsCanceledAndCancelsPayment() {
        CustomPortal p = portal(CustomPortalStatus.SUSPENDED);
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        CustomPortalDTO dto = service.cancel(99L, 2L, "encerrado pelo cliente");

        assertEquals("CANCELED", dto.status());
        assertEquals(CustomPortalPaymentStatus.CANCELED, p.getPaymentStatus());
    }

    @Test
    void updateSubscription_rejectedWhenCanceled() {
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(portal(CustomPortalStatus.CANCELED)));

        UpdateCustomPortalSubscriptionDTO body = new UpdateCustomPortalSubscriptionDTO(
                "Plano Pro", new BigDecimal("199.90"), LocalDate.now().plusMonths(1), null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateSubscription(2L, body));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void updateSubscription_resetsRenewalReminderWhenDueDateChanges() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        p.setLastRenewalReminderFor(p.getNextDueDate());
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        LocalDate newDue = LocalDate.now().plusMonths(2);
        service.updateSubscription(2L, new UpdateCustomPortalSubscriptionDTO(
                "Plano Pro", new BigDecimal("249.90"), newDue, CustomPortalPaymentStatus.OVERDUE));

        assertEquals(newDue, p.getNextDueDate());
        assertNull(p.getLastRenewalReminderFor());
        assertEquals(CustomPortalPaymentStatus.OVERDUE, p.getPaymentStatus());
    }

    // ── job de vencimento ──────────────────────────────────────────

    @Test
    void notifyUpcomingRenewals_sendsOncePerCycle() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        p.setNextDueDate(LocalDate.now().plusDays(3));
        p.setLastRenewalReminderFor(null);
        when(customPortalRepository.findByStatusAndNextDueDateLessThanEqual(
                eq(CustomPortalStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(p));

        service.notifyUpcomingRenewals();
        service.notifyUpcomingRenewals(); // segunda passada: trava já marcada

        verify(notificationService, times(1))
                .notifyCustomPortalRenewalDue(eq(companyUser), any(LocalDate.class));
        assertEquals(p.getNextDueDate(), p.getLastRenewalReminderFor());
    }

    @Test
    void getOverview_canRequestFalseWhenPendingExists() {
        CustomPortalRequest request = pendingRequest();
        when(requestRepository.findFirstByCompanyIdOrderByRequestedAtDesc(1L))
                .thenReturn(Optional.of(request));
        when(customPortalRepository.findByCompanyId(1L)).thenReturn(Optional.empty());

        var overview = service.getOverviewForUser(5L);

        assertTrue(!overview.canRequest());
        assertEquals("PENDING", overview.latestRequest().status());
        assertNull(overview.portal());
    }

    @Test
    void getOverview_canRequestTrueWhenNothingYet() {
        when(requestRepository.findFirstByCompanyIdOrderByRequestedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(customPortalRepository.findByCompanyId(1L)).thenReturn(Optional.empty());

        var overview = service.getOverviewForUser(5L);

        assertTrue(overview.canRequest());
    }

    // ── customização visual (Prompt 2) ─────────────────────────────

    private UpdateCustomPortalBrandingDTO branding(
            String color, List<CustomPortalSectionDTO> sections) {
        return branding(color, sections, null);
    }

    private UpdateCustomPortalBrandingDTO branding(
            String color, List<CustomPortalSectionDTO> sections,
            com.main.nexus.dto.SocialLinksDTO social) {
        return new UpdateCustomPortalBrandingDTO(
                "Carreiras Acme", color, "Somos uma empresa incrível.", sections, social);
    }

    @Test
    void updateBranding_persistsFieldsAndReplacesSections() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        p.getSections().add(new com.main.nexus.model.CustomPortalSection("antiga", "x"));
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        CustomPortalDTO dto = service.updateBrandingAsAdmin(2L, branding("#4F46E5", List.of(
                new CustomPortalSectionDTO("Nossos valores", "Transparência."),
                new CustomPortalSectionDTO("Benefícios", "Vale-refeição."))));

        assertEquals("Carreiras Acme", dto.displayName());
        assertEquals("#4f46e5", dto.primaryColor()); // normalizado p/ minúsculas
        assertEquals(2, p.getSections().size());
        assertEquals("Nossos valores", p.getSections().get(0).getTitle());
        assertEquals("Benefícios", p.getSections().get(1).getTitle());
    }

    @Test
    void updateBranding_persistsSocialLinksAndClearsBlanks() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        p.getSocialLinks().setInstagram("https://old.example");
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        var social = new com.main.nexus.dto.SocialLinksDTO(
                "https://neostack.dev", null, "  ",
                "https://facebook.com/neostack", null, null, "https://github.com/neostack");
        CustomPortalDTO dto = service.updateBrandingAsAdmin(2L, branding("#4f46e5", List.of(), social));

        assertEquals("https://neostack.dev", dto.socialLinks().website());
        assertEquals("https://github.com/neostack", dto.socialLinks().github());
        assertNull(dto.socialLinks().instagram()); // "  " -> limpo
        assertNull(dto.socialLinks().linkedin());
        assertNull(p.getSocialLinks().getInstagram());
    }

    @Test
    void updateBranding_rejectsSocialLinkWithoutScheme() {
        when(customPortalRepository.findById(2L))
                .thenReturn(Optional.of(portal(CustomPortalStatus.ACTIVE)));

        var social = new com.main.nexus.dto.SocialLinksDTO(
                null, "linkedin.com/company/neostack", null, null, null, null, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateBrandingAsAdmin(2L, branding("#4f46e5", List.of(), social)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateBranding_rejectsInvalidColor() {
        when(customPortalRepository.findById(2L))
                .thenReturn(Optional.of(portal(CustomPortalStatus.ACTIVE)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateBrandingAsAdmin(2L, branding("azul", List.of())));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateBranding_acceptsNullColorAsCleared() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        p.setPrimaryColor("#123456");
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        CustomPortalDTO dto = service.updateBrandingAsAdmin(2L, branding(null, List.of()));
        assertNull(dto.primaryColor());
    }

    @Test
    void updateBranding_rejectsSectionWithoutTitle() {
        when(customPortalRepository.findById(2L))
                .thenReturn(Optional.of(portal(CustomPortalStatus.ACTIVE)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateBrandingAsAdmin(2L, branding("#4f46e5", List.of(
                        new CustomPortalSectionDTO("  ", "texto sem título")))));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateBranding_dropsFullyEmptySections() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        service.updateBrandingAsAdmin(2L, branding("#4f46e5", List.of(
                new CustomPortalSectionDTO("Cultura", "..."),
                new CustomPortalSectionDTO("", "  "))));

        assertEquals(1, p.getSections().size());
    }

    @Test
    void updateBranding_rejectsTooManySections() {
        when(customPortalRepository.findById(2L))
                .thenReturn(Optional.of(portal(CustomPortalStatus.ACTIVE)));

        java.util.List<CustomPortalSectionDTO> many = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            many.add(new CustomPortalSectionDTO("Seção " + i, "..."));
        }

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateBrandingAsAdmin(2L, branding("#4f46e5", many)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateBrandingForUser_404WhenCompanyHasNoPortal() {
        when(customPortalRepository.findByCompanyId(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateBrandingForUser(5L, branding("#4f46e5", List.of())));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void setBrandingImage_uploadsAndStoresUrl() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));
        when(supabaseStorageService.uploadCustomPortalImage(any(), eq(2L), eq("logo")))
                .thenReturn("https://supabase/logo.png");

        CustomPortalDTO dto = service.setBrandingImageAsAdmin(2L, BrandingImageKind.LOGO,
                new MockMultipartFile("file", "l.png", "image/png", new byte[] {1, 2, 3}));

        assertEquals("https://supabase/logo.png", dto.logoUrl());
        assertEquals("https://supabase/logo.png", p.getLogoUrl());
    }

    @Test
    void clearBrandingImage_removesUrlAndDeletesFromStorage() {
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        p.setBannerUrl("https://supabase/banner.png");
        when(customPortalRepository.findById(2L)).thenReturn(Optional.of(p));

        CustomPortalDTO dto = service.clearBrandingImageAsAdmin(2L, BrandingImageKind.BANNER);

        assertNull(dto.bannerUrl());
        assertNull(p.getBannerUrl());
        verify(supabaseStorageService).deleteCustomPortalImage("https://supabase/banner.png");
    }

    // ── página pública / resolução de tenant (Prompt 3) ────────────

    @Test
    void getPublicBySubdomain_returnsDtoForAnyLifecycleStatus() {
        CustomPortal p = portal(CustomPortalStatus.SUSPENDED);
        p.setDisplayName("Carreiras Acme");
        when(customPortalRepository.findBySubdomainIgnoreCase("acme")).thenReturn(Optional.of(p));

        var dto = service.getPublicBySubdomain("  ACME ");

        assertEquals("acme", dto.subdomain());
        assertEquals("SUSPENDED", dto.status());
        assertEquals("Carreiras Acme", dto.displayName());
        assertEquals(company.getId(), dto.companyId());
    }

    @Test
    void getPublicBySubdomain_404WhenSubdomainUnknown() {
        when(customPortalRepository.findBySubdomainIgnoreCase("nope")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getPublicBySubdomain("nope"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getPublicBySubdomain_404WhenOwnerCompanyNotApproved() {
        company.setStatus(CompanyStatus.PENDING);
        CustomPortal p = portal(CustomPortalStatus.ACTIVE);
        when(customPortalRepository.findBySubdomainIgnoreCase("acme")).thenReturn(Optional.of(p));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getPublicBySubdomain("acme"));
        assertEquals(404, ex.getStatusCode().value());
    }
}
