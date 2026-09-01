package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.model.CommissionCharge;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyFiscalProfile;
import com.main.nexus.model.FiscalConfig;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.NfseInvoice;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CommissionChargeStatus;
import com.main.nexus.model.enums.CompanyType;
import com.main.nexus.model.enums.NfseInvoiceStatus;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyFiscalProfileRepository;
import com.main.nexus.repository.FiscalConfigRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NfseServiceTest {

    @Mock private EnotasClient enotas;
    @Mock private FiscalConfigRepository fiscalConfigRepository;
    @Mock private CompanyFiscalProfileRepository fiscalProfileRepository;
    @Mock private NfseInvoiceRepository invoiceRepository;
    @Mock private CommissionChargeRepository chargeRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private NfseService service;

    private Company company;
    private CompanyFiscalProfile fiscalProfile;
    private CommissionCharge charge;
    private FiscalConfig config;

    @BeforeEach
    void setUp() {
        // Padrão: modo simulado (sem eNotas).
        ReflectionTestUtils.setField(service, "simulate", true);
        when(enotas.hasCredentials()).thenReturn(false);

        config = new FiscalConfig();
        config.setId(FiscalConfig.SINGLETON_ID);
        config.setDefaultServiceDescription("Comissão Nexus");
        when(fiscalConfigRepository.findById(FiscalConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(fiscalConfigRepository.save(any(FiscalConfig.class))).thenAnswer(i -> i.getArgument(0));

        User user = new User();
        user.setId(7L);
        user.setEmail("acme@example.com");

        company = new Company();
        company.setId(1L);
        company.setCompanyName("Acme");
        company.setTaxId("12345678000199");
        company.setType(CompanyType.INDIVIDUAL);
        company.setUser(user);
        company.setCity("São Paulo");
        company.setUf("SP");
        company.setCep("01000-000");

        Professional prof = new Professional();
        prof.setId(3L);
        prof.setName("Dev Silva");

        Project project = new Project();
        project.setTitle("App novo");
        project.setCompany(company);

        Match match = new Match();
        match.setId(42L);
        match.setProject(project);
        match.setProfessional(prof);

        MatchConfirmation confirmation = new MatchConfirmation();
        confirmation.setId(10L);
        confirmation.setMatch(match);

        charge = new CommissionCharge();
        charge.setId(100L);
        charge.setMatchConfirmation(confirmation);
        charge.setCompany(company);
        charge.setAmount(new BigDecimal("100.00"));
        charge.setStatus(CommissionChargeStatus.PAID);

        fiscalProfile = new CompanyFiscalProfile();
        fiscalProfile.setCompany(company);
        fiscalProfile.setFiscalEmail("fiscal@acme.com");

        when(chargeRepository.findById(100L)).thenReturn(Optional.of(charge));
        when(fiscalProfileRepository.findByCompanyId(1L)).thenReturn(Optional.of(fiscalProfile));
        when(fiscalProfileRepository.save(any(CompanyFiscalProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepository.findByCommissionChargeId(100L)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(NfseInvoice.class))).thenAnswer(i -> {
            NfseInvoice n = i.getArgument(0);
            if (n.getId() == null) n.setId(500L);
            return n;
        });
        when(invoiceRepository.saveAndFlush(any(NfseInvoice.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void issueFor_chargeNotPaid_isNoop() {
        charge.setStatus(CommissionChargeStatus.PROCESSING);

        service.issueFor(100L);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void issueFor_simulateModeWithCompleteData_goesProcessingWithFakeId() {
        service.issueFor(100L);

        assertEquals(NfseInvoiceStatus.PROCESSING, lastInvoice().getStatus());
        assertEquals("SIM-500", lastInvoice().getEnotasId());
        assertEquals(1, lastInvoice().getAttempts());
        verify(enotas, never()).createNfse(anyString(), any());
    }

    @Test
    void issueFor_incompleteFiscalData_failsAndNotifies() {
        // PJ sem endereço -> dados incompletos.
        company.setType(CompanyType.LEGAL_ENTITY);
        fiscalProfile.setStreet(null);
        fiscalProfile.setNumber(null);
        fiscalProfile.setDistrict(null);

        service.issueFor(100L);

        assertEquals(NfseInvoiceStatus.FAILED, lastInvoice().getStatus());
        assertNotNull(lastInvoice().getFailureReason());
        verify(notificationService).notifyNfseFailed(any(User.class), anyString());
        verify(enotas, never()).createNfse(anyString(), any());
    }

    @Test
    void issueFor_liveModeWithCompleteData_callsEnotasAndStoresId() {
        ReflectionTestUtils.setField(service, "simulate", false);
        when(enotas.hasCredentials()).thenReturn(true);
        config.setEnotasEmpresaId("emp_1");
        when(enotas.createNfse(eq("emp_1"), any())).thenReturn("enotas-guid-1");

        service.issueFor(100L);

        assertEquals(NfseInvoiceStatus.PROCESSING, lastInvoice().getStatus());
        assertEquals("enotas-guid-1", lastInvoice().getEnotasId());
    }

    @Test
    void simulateOutcome_whenNotSimulated_throwsConflict() {
        ReflectionTestUtils.setField(service, "simulate", false);
        when(enotas.hasCredentials()).thenReturn(true);
        config.setEnotasEmpresaId("emp_1");

        NfseInvoice inv = existingInvoice(NfseInvoiceStatus.PROCESSING);
        when(invoiceRepository.findById(500L)).thenReturn(Optional.of(inv));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.simulateOutcome(500L, "authorized"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void simulateOutcome_authorized_marksIssuedAndNotifies() {
        NfseInvoice inv = existingInvoice(NfseInvoiceStatus.PROCESSING);
        when(invoiceRepository.findById(500L)).thenReturn(Optional.of(inv));

        service.simulateOutcome(500L, "authorized");

        assertEquals(NfseInvoiceStatus.ISSUED, inv.getStatus());
        assertNotNull(inv.getLinkPdf());
        assertNotNull(inv.getIssuedAt());
        verify(notificationService).notifyNfseIssued(any(User.class), eq("App novo"), anyString());
    }

    @Test
    void simulateOutcome_denied_marksFailedAndNotifies() {
        NfseInvoice inv = existingInvoice(NfseInvoiceStatus.PROCESSING);
        when(invoiceRepository.findById(500L)).thenReturn(Optional.of(inv));

        service.simulateOutcome(500L, "denied");

        assertEquals(NfseInvoiceStatus.FAILED, inv.getStatus());
        verify(notificationService).notifyNfseFailed(any(User.class), anyString());
    }

    @Test
    void handleWebhook_liveAuthorized_marksIssued() {
        ReflectionTestUtils.setField(service, "simulate", false);
        when(enotas.hasCredentials()).thenReturn(true);
        config.setEnotasEmpresaId("emp_1");

        NfseInvoice inv = existingInvoice(NfseInvoiceStatus.PROCESSING);
        inv.setEnotasId("enotas-guid-1");
        when(invoiceRepository.findByEnotasId("enotas-guid-1")).thenReturn(Optional.of(inv));
        when(enotas.getNfse("emp_1", "enotas-guid-1")).thenReturn(new EnotasClient.EnotasNfe(
                "enotas-guid-1", "Autorizada", "42", "1",
                "http://x/pdf", "http://x/xml", "ABC123", null));

        service.handleWebhook("enotas-guid-1");

        assertEquals(NfseInvoiceStatus.ISSUED, inv.getStatus());
        assertEquals("42", inv.getNumero());
        assertEquals("http://x/pdf", inv.getLinkPdf());
    }

    // ── helpers ────────────────────────────────────────────────

    private NfseInvoice existingInvoice(NfseInvoiceStatus status) {
        NfseInvoice inv = new NfseInvoice();
        inv.setId(500L);
        inv.setCommissionCharge(charge);
        inv.setCompany(company);
        inv.setStatus(status);
        return inv;
    }

    private NfseInvoice lastInvoice() {
        var captor = org.mockito.ArgumentCaptor.forClass(NfseInvoice.class);
        verify(invoiceRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
