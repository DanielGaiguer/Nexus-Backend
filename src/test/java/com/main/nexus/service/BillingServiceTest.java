package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.model.CommissionCharge;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyBillingProfile;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CommissionChargeStatus;
import com.main.nexus.model.enums.PaymentBlockReason;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyBillingProfileRepository;
import com.main.nexus.repository.CompanyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock private MercadoPagoClient mercadoPago;
    @Mock private CompanyBillingProfileRepository profileRepository;
    @Mock private CommissionChargeRepository chargeRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private BillingService service;

    private Company company;
    private CompanyBillingProfile profile;
    private CommissionCharge charge;

    @BeforeEach
    void setUp() {
        // Por padrão: Mercado Pago "de verdade" habilitado.
        ReflectionTestUtils.setField(service, "billingEnabled", true);
        ReflectionTestUtils.setField(service, "simulate", false);
        when(mercadoPago.hasCredentials()).thenReturn(true);

        User user = new User();
        user.setId(7L);
        user.setEmail("acme@example.com");

        company = new Company();
        company.setId(1L);
        company.setCompanyName("Acme");
        company.setUser(user);

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
        confirmation.setConfirmedAmount(new BigDecimal("1000.00"));

        profile = new CompanyBillingProfile();
        profile.setCompany(company);
        profile.setMpCustomerId("cust_1");
        profile.setMpCardId("card_1");

        charge = new CommissionCharge();
        charge.setId(100L);
        charge.setMatchConfirmation(confirmation);
        charge.setCompany(company);
        charge.setBaseAmount(new BigDecimal("1000.00"));
        charge.setPercentage(new BigDecimal("10.00"));
        charge.setAmount(new BigDecimal("100.00"));
        charge.setStatus(CommissionChargeStatus.PENDING);

        when(chargeRepository.findById(100L)).thenReturn(Optional.of(charge));
        when(chargeRepository.save(any(CommissionCharge.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(chargeRepository.saveAndFlush(any(CommissionCharge.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.save(any(CompanyBillingProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.findByCompanyId(1L)).thenReturn(Optional.of(profile));
    }

    @Test
    void processCharge_approved_marksPaidAndDoesNotBlock() {
        when(mercadoPago.createCardTokenFromSavedCard("card_1")).thenReturn("tok_1");
        when(mercadoPago.createPayment(any(), eq("tok_1"), eq("cust_1"), anyString(), anyString(), anyString()))
                .thenReturn(new MercadoPagoClient.Payment("MP1", "approved", "accredited", "commission-charge-100"));
        when(chargeRepository.existsByCompanyIdAndStatusIn(eq(1L), any())).thenReturn(false);

        service.processCharge(100L);

        assertEquals(CommissionChargeStatus.PAID, charge.getStatus());
        assertEquals("MP1", charge.getMpPaymentId());
        assertNull(charge.getFailureReason());
        assertFalse(Boolean.TRUE.equals(profile.getPaymentBlocked()));
        verify(notificationService).notifyCommissionPaid(any(User.class), eq(new BigDecimal("100.00")), eq("App novo"));
    }

    @Test
    void processCharge_rejected_marksFailedAndBlocks() {
        when(mercadoPago.createCardTokenFromSavedCard("card_1")).thenReturn("tok_1");
        when(mercadoPago.createPayment(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new MercadoPagoClient.Payment("MP2", "rejected", "cc_rejected_insufficient_amount", "x"));

        service.processCharge(100L);

        assertEquals(CommissionChargeStatus.FAILED, charge.getStatus());
        assertTrue(profile.getPaymentBlocked());
        assertEquals(PaymentBlockReason.CHARGE_DECLINED, profile.getBlockReason());
        verify(notificationService).notifyCommissionChargeFailed(any(User.class), anyString());
    }

    @Test
    void processCharge_noCard_staysPendingAndBlocks() {
        profile.setMpCustomerId(null);
        profile.setMpCardId(null);

        service.processCharge(100L);

        assertEquals(CommissionChargeStatus.PENDING, charge.getStatus());
        assertTrue(profile.getPaymentBlocked());
        assertEquals(PaymentBlockReason.NO_CARD_ON_FILE, profile.getBlockReason());
        verify(mercadoPago, never()).createPayment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processCharge_billingDisabled_isNoop() {
        ReflectionTestUtils.setField(service, "billingEnabled", false);

        service.processCharge(100L);

        assertEquals(CommissionChargeStatus.PENDING, charge.getStatus());
        verify(mercadoPago, never()).createCardTokenFromSavedCard(any());
    }

    @Test
    void handleWebhook_approved_marksPaidAndUnblocks() {
        charge.setStatus(CommissionChargeStatus.PROCESSING);
        charge.setMpPaymentId("MP9");
        profile.setPaymentBlocked(true);
        profile.setBlockReason(PaymentBlockReason.CHARGE_DECLINED);
        when(mercadoPago.getPayment("MP9"))
                .thenReturn(new MercadoPagoClient.Payment("MP9", "approved", "accredited", "commission-charge-100"));
        when(chargeRepository.findByMpPaymentId("MP9")).thenReturn(Optional.of(charge));
        when(chargeRepository.existsByCompanyIdAndStatusIn(eq(1L), any())).thenReturn(false);

        service.handleWebhook("MP9");

        assertEquals(CommissionChargeStatus.PAID, charge.getStatus());
        assertFalse(Boolean.TRUE.equals(profile.getPaymentBlocked()));
    }

    @Test
    void assertCanCloseNewHire_blocked_throwsPaymentRequired() {
        profile.setPaymentBlocked(true);
        profile.setBlockReason(PaymentBlockReason.CHARGE_DECLINED);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assertCanCloseNewHire(company));
        assertEquals(402, ex.getStatusCode().value());
    }

    @Test
    void assertCanCloseNewHire_notBlocked_passes() {
        // profile presente e não bloqueado
        service.assertCanCloseNewHire(company);
        // sem profile
        when(profileRepository.findByCompanyId(1L)).thenReturn(Optional.empty());
        service.assertCanCloseNewHire(company);
    }

    @Test
    void createCharge_isIdempotentPerConfirmation() {
        when(chargeRepository.findByMatchConfirmationId(10L)).thenReturn(Optional.of(charge));

        CommissionCharge result = service.createCharge(
                charge.getMatchConfirmation(),
                new BigDecimal("1000.00"), new BigDecimal("10.00"), new BigDecimal("100.00"));

        assertSame(charge, result);
        verify(chargeRepository, never()).save(any(CommissionCharge.class));
    }

    @Test
    void simulateOutcome_whenNotSimulated_throwsConflict() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.simulateOutcome(100L, "approved"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void simulateOutcome_approved_marksPaidWithoutMercadoPago() {
        ReflectionTestUtils.setField(service, "billingEnabled", false);
        ReflectionTestUtils.setField(service, "simulate", true);
        charge.setStatus(CommissionChargeStatus.PROCESSING);
        charge.setMpPaymentId("SIM-100");
        when(chargeRepository.existsByCompanyIdAndStatusIn(eq(1L), any())).thenReturn(false);

        service.simulateOutcome(100L, "approved");

        assertEquals(CommissionChargeStatus.PAID, charge.getStatus());
        verify(mercadoPago, never()).getPayment(any());
        verify(mercadoPago, never()).createPayment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void simulateOutcome_rejected_marksFailedAndBlocks() {
        ReflectionTestUtils.setField(service, "billingEnabled", false);
        ReflectionTestUtils.setField(service, "simulate", true);
        charge.setStatus(CommissionChargeStatus.PROCESSING);

        service.simulateOutcome(100L, "rejected");

        assertEquals(CommissionChargeStatus.FAILED, charge.getStatus());
        assertTrue(profile.getPaymentBlocked());
        assertEquals(PaymentBlockReason.CHARGE_DECLINED, profile.getBlockReason());
    }

    @Test
    void saveCard_inSimulateMode_storesFakeCardWithoutMercadoPago() {
        ReflectionTestUtils.setField(service, "billingEnabled", false);
        ReflectionTestUtils.setField(service, "simulate", true);
        when(chargeRepository.findFirstByCompanyIdAndStatusInOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(Optional.empty());

        var status = service.saveCard(company, "SIMULATED");

        assertTrue(status.hasCard());
        assertEquals("0000", profile.getCardLast4());
        verify(mercadoPago, never()).getOrCreateCustomer(any(), any());
        verify(mercadoPago, never()).saveCard(any(), any());
    }
}
