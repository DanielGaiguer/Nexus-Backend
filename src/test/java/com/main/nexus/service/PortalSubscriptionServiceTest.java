package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.model.Company;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.PortalSubscriptionCharge;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import com.main.nexus.model.enums.CustomPortalStatus;
import com.main.nexus.model.enums.PortalSubscriptionChargeStatus;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalStatusHistoryRepository;
import com.main.nexus.repository.PortalSubscriptionChargeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalSubscriptionServiceTest {

    @Mock private MercadoPagoClient mercadoPago;
    @Mock private CustomPortalRepository customPortalRepository;
    @Mock private CustomPortalStatusHistoryRepository historyRepository;
    @Mock private PortalSubscriptionChargeRepository chargeRepository;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PortalSubscriptionService service;

    private Company company;
    private CustomPortal portal;

    @BeforeEach
    void setUp() {
        // Padrão: modo simulado.
        ReflectionTestUtils.setField(service, "billingEnabled", true);
        ReflectionTestUtils.setField(service, "simulate", true);
        when(mercadoPago.hasCredentials()).thenReturn(false);
        when(mercadoPago.publicKey()).thenReturn("");

        User user = new User();
        user.setId(5L);
        user.setEmail("acme@example.com");

        company = new Company();
        company.setId(1L);
        company.setCompanyName("Acme");
        company.setUser(user);

        portal = new CustomPortal();
        portal.setId(9L);
        portal.setCompany(company);
        portal.setStatus(CustomPortalStatus.ACTIVE);
        portal.setSubdomain("acme");
        portal.setPlanName("Plano Pro");
        portal.setPlanPrice(new BigDecimal("199.90"));
        portal.setSubscriptionStartDate(LocalDate.now());
        portal.setNextDueDate(LocalDate.now());
        portal.setPaymentStatus(CustomPortalPaymentStatus.UP_TO_DATE);

        when(customPortalRepository.findByCompanyId(1L)).thenReturn(Optional.of(portal));
        when(customPortalRepository.save(any(CustomPortal.class))).thenAnswer(i -> i.getArgument(0));
        when(chargeRepository.save(any(PortalSubscriptionCharge.class))).thenAnswer(i -> {
            PortalSubscriptionCharge c = i.getArgument(0);
            if (c.getId() == null) c.setId(700L);
            return c;
        });
        when(chargeRepository.findFirstByCustomPortalIdAndStatusInOrderByCreatedAtDesc(eq(9L), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void saveCard_simulated_setsFakeCardAndPreapproval() {
        service.saveCard(company, "tok_123");

        ArgumentCaptor<CustomPortal> captor = ArgumentCaptor.forClass(CustomPortal.class);
        verify(customPortalRepository).save(captor.capture());
        CustomPortal saved = captor.getValue();
        assertEquals("SIM-9", saved.getMpPreapprovalId());
        assertEquals("0000", saved.getSubscriptionCardLast4());
        verify(mercadoPago, never()).createPreapproval(any(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void saveCard_live_createsPreapprovalAndStoresCardSummary() {
        ReflectionTestUtils.setField(service, "simulate", false);
        when(mercadoPago.hasCredentials()).thenReturn(true);
        when(mercadoPago.getCardToken("tok_123"))
                .thenReturn(new MercadoPagoClient.CardTokenInfo("4321", "visa"));
        when(mercadoPago.createPreapproval(any(), eq("tok_123"), eq("acme@example.com"),
                any(), anyString(), eq("portal-sub-9")))
                .thenReturn(new MercadoPagoClient.Preapproval("pre_1", "authorized", null));

        service.saveCard(company, "tok_123");

        assertEquals("pre_1", portal.getMpPreapprovalId());
        assertEquals("4321", portal.getSubscriptionCardLast4());
        assertEquals("visa", portal.getSubscriptionCardBrand());
    }

    @Test
    void simulateOutcome_whenNotSimulated_throwsConflict() {
        ReflectionTestUtils.setField(service, "simulate", false);
        when(mercadoPago.hasCredentials()).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> service.simulateOutcome(700L, "approved"));
    }

    @Test
    void simulateOutcome_approved_marksPaidAdvancesDueDateAndPublishesEvent() {
        LocalDate due = LocalDate.of(2026, 9, 1);
        PortalSubscriptionCharge charge = processingCharge(due);
        when(chargeRepository.findById(700L)).thenReturn(Optional.of(charge));

        service.simulateOutcome(700L, "approved");

        assertEquals(PortalSubscriptionChargeStatus.PAID, charge.getStatus());
        assertNotNull(charge.getPaidAt());
        assertEquals(CustomPortalPaymentStatus.UP_TO_DATE, portal.getPaymentStatus());
        assertEquals(due.plusMonths(1), portal.getNextDueDate());
        assertNull(portal.getPaymentGraceUntil());
        verify(notificationService).notifyPortalSubscriptionCharged(any(User.class), eq(charge.getAmount()));
        verify(eventPublisher).publishEvent(any(PortalSubscriptionChargePaidEvent.class));
    }

    @Test
    void simulateOutcome_rejected_marksFailedSetsOverdueAndGrace() {
        PortalSubscriptionCharge charge = processingCharge(LocalDate.now());
        when(chargeRepository.findById(700L)).thenReturn(Optional.of(charge));

        service.simulateOutcome(700L, "rejected");

        assertEquals(PortalSubscriptionChargeStatus.FAILED, charge.getStatus());
        assertEquals(CustomPortalPaymentStatus.OVERDUE, portal.getPaymentStatus());
        assertNotNull(portal.getPaymentGraceUntil());
        verify(notificationService).notifyPortalSubscriptionPaymentFailed(any(User.class), any(LocalDate.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void runBillingCycle_simulate_createsDueChargeForActivePortalWithCard() {
        portal.setMpPreapprovalId("SIM-9");
        portal.setNextDueDate(LocalDate.now());
        when(customPortalRepository.findByStatusAndNextDueDateLessThanEqual(
                eq(CustomPortalStatus.ACTIVE), any())).thenReturn(List.of(portal));
        when(customPortalRepository.findByPaymentStatusAndStatus(
                CustomPortalPaymentStatus.OVERDUE, CustomPortalStatus.ACTIVE)).thenReturn(List.of());
        when(chargeRepository.existsByCustomPortalIdAndDueDateAndStatusIn(eq(9L), any(), any()))
                .thenReturn(false);

        service.runBillingCycle();

        ArgumentCaptor<PortalSubscriptionCharge> captor =
                ArgumentCaptor.forClass(PortalSubscriptionCharge.class);
        verify(chargeRepository).save(captor.capture());
        assertEquals(PortalSubscriptionChargeStatus.PROCESSING, captor.getValue().getStatus());
        assertEquals(new BigDecimal("199.90"), captor.getValue().getAmount());
    }

    @Test
    void runBillingCycle_portalWithoutCard_startsGraceAndNotifies() {
        portal.setMpPreapprovalId(null);
        portal.setPaymentStatus(CustomPortalPaymentStatus.UP_TO_DATE);
        portal.setNextDueDate(LocalDate.now());
        when(customPortalRepository.findByStatusAndNextDueDateLessThanEqual(
                eq(CustomPortalStatus.ACTIVE), any())).thenReturn(List.of(portal));
        when(customPortalRepository.findByPaymentStatusAndStatus(
                CustomPortalPaymentStatus.OVERDUE, CustomPortalStatus.ACTIVE)).thenReturn(List.of());

        service.runBillingCycle();

        assertEquals(CustomPortalPaymentStatus.OVERDUE, portal.getPaymentStatus());
        assertNotNull(portal.getPaymentGraceUntil());
        verify(notificationService).notifyPortalSubscriptionPaymentFailed(any(User.class), any(LocalDate.class));
        verify(chargeRepository, never()).save(any(PortalSubscriptionCharge.class));
    }

    @Test
    void runBillingCycle_suspendsPortalAfterGraceExpires() {
        portal.setPaymentStatus(CustomPortalPaymentStatus.OVERDUE);
        portal.setPaymentGraceUntil(LocalDate.now().minusDays(1));
        when(customPortalRepository.findByStatusAndNextDueDateLessThanEqual(
                eq(CustomPortalStatus.ACTIVE), any())).thenReturn(List.of());
        when(customPortalRepository.findByPaymentStatusAndStatus(
                CustomPortalPaymentStatus.OVERDUE, CustomPortalStatus.ACTIVE)).thenReturn(List.of(portal));

        service.runBillingCycle();

        assertEquals(CustomPortalStatus.SUSPENDED, portal.getStatus());
        verify(historyRepository).save(any());
        verify(notificationService).notifyPortalSuspendedForNonPayment(any(User.class));
    }

    private PortalSubscriptionCharge processingCharge(LocalDate due) {
        PortalSubscriptionCharge c = new PortalSubscriptionCharge();
        c.setId(700L);
        c.setCustomPortal(portal);
        c.setCompany(company);
        c.setAmount(new BigDecimal("199.90"));
        c.setDueDate(due);
        c.setStatus(PortalSubscriptionChargeStatus.PROCESSING);
        return c;
    }
}
