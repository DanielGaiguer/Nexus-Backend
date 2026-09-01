package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.AdminFinanceOverviewDTO;
import com.main.nexus.dto.ContractorCommissionStatusDTO;
import com.main.nexus.dto.ContractorFinanceOverviewDTO;
import com.main.nexus.model.CommissionPolicy;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyBillingProfile;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.enums.CommissionChargeStatus;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import com.main.nexus.model.enums.NfseInvoiceStatus;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyBillingProfileRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.MatchConfirmationRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.PortalSubscriptionChargeRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceServiceTest {

    @Mock private CommissionChargeRepository chargeRepository;
    @Mock private NfseInvoiceRepository invoiceRepository;
    @Mock private MatchConfirmationRepository confirmationRepository;
    @Mock private CompanyBillingProfileRepository billingProfileRepository;
    @Mock private BillingService billingService;
    @Mock private CommissionService commissionService;
    @Mock private PortalSubscriptionChargeRepository portalChargeRepository;
    @Mock private CustomPortalRepository customPortalRepository;

    @InjectMocks private FinanceService service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        company.setCompanyName("Acme");

        when(billingService.isBillingEnabled()).thenReturn(true);
        when(billingService.isSimulated()).thenReturn(false);
        when(billingService.isLive()).thenReturn(true);

        // Mensalidades de plataforma -- zeradas por padrão (origem separada).
        when(portalChargeRepository.sumAmountByStatus(any())).thenReturn(BigDecimal.ZERO);
        when(portalChargeRepository.sumAmountByStatusIn(anyList())).thenReturn(BigDecimal.ZERO);
        when(portalChargeRepository.sumAmountByCompanyAndStatus(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(portalChargeRepository.sumAmountByCompanyAndStatusIn(any(), anyList()))
                .thenReturn(BigDecimal.ZERO);
        when(customPortalRepository.existsByCompanyId(any())).thenReturn(false);
    }

    @Test
    void contractorOverview_aggregatesPaidPendingAndAwaitingEstimate() {
        when(chargeRepository.sumAmountByCompanyAndStatus(1L, CommissionChargeStatus.PAID))
                .thenReturn(new BigDecimal("300.00"));
        when(chargeRepository.countByCompanyIdAndStatus(1L, CommissionChargeStatus.PAID))
                .thenReturn(3L);
        when(chargeRepository.sumAmountByCompanyAndStatusIn(eq(1L), anyList()))
                .thenReturn(new BigDecimal("50.00"));
        when(chargeRepository.countByCompanyIdAndStatusIn(eq(1L), anyList())).thenReturn(2L);

        when(commissionService.getContractorStatus(company)).thenReturn(
                new ContractorCommissionStatusDTO(3, 4, 0, true, new BigDecimal("10.00")));

        Professional prof = new Professional();
        prof.setName("Dev Silva");
        Project project = new Project();
        project.setTitle("App novo");
        Match match = new Match();
        match.setId(42L);
        match.setProject(project);
        match.setProfessional(prof);
        MatchConfirmation mc = new MatchConfirmation();
        mc.setMatch(match);
        mc.setOpenedAt(LocalDateTime.now().minusDays(5));
        mc.setDeadline(LocalDateTime.now().plusDays(2));
        mc.setSuggestedAmount(new BigDecimal("2000.00"));

        when(confirmationRepository.findByMatchProjectCompanyIdAndStatusOrderByOpenedAtAsc(
                1L, MatchConfirmationStatus.AWAITING_RESPONSES)).thenReturn(List.of(mc));
        when(billingProfileRepository.findByCompanyId(1L)).thenReturn(Optional.empty());
        when(invoiceRepository.countByCompanyIdAndStatus(1L, NfseInvoiceStatus.ISSUED))
                .thenReturn(2L);
        when(invoiceRepository.countByCompanyIdAndStatusIn(eq(1L), anyList())).thenReturn(1L);

        ContractorFinanceOverviewDTO dto = service.contractorOverview(company);

        assertEquals(new BigDecimal("300.00"), dto.totalPaid());
        assertEquals(3L, dto.paidCount());
        assertEquals(new BigDecimal("50.00"), dto.totalPending());
        assertEquals(1L, dto.awaitingConfirmationCount());
        // 2000.00 * 10% = 200.00
        assertEquals(new BigDecimal("200.00"), dto.awaitingConfirmationEstimated());
        assertEquals("App novo", dto.awaitingConfirmations().get(0).projectTitle());
        assertEquals(0, dto.freeHiresRemaining());
        assertTrue(dto.commissionApplies());
        assertFalse(dto.blocked());
        assertEquals(2L, dto.invoicesIssuedCount());
    }

    @Test
    void contractorOverview_blockedWhenProfileFlagged() {
        when(chargeRepository.sumAmountByCompanyAndStatus(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(chargeRepository.sumAmountByCompanyAndStatusIn(any(), anyList()))
                .thenReturn(BigDecimal.ZERO);
        when(commissionService.getContractorStatus(company)).thenReturn(
                new ContractorCommissionStatusDTO(3, 5, 0, true, new BigDecimal("10.00")));
        when(confirmationRepository.findByMatchProjectCompanyIdAndStatusOrderByOpenedAtAsc(
                any(), any())).thenReturn(List.of());

        CompanyBillingProfile profile = new CompanyBillingProfile();
        profile.setCompany(company);
        profile.setPaymentBlocked(true);
        when(billingProfileRepository.findByCompanyId(1L)).thenReturn(Optional.of(profile));

        ContractorFinanceOverviewDTO dto = service.contractorOverview(company);

        assertTrue(dto.blocked());
        assertEquals(0L, dto.awaitingConfirmationCount());
        assertEquals(BigDecimal.ZERO, dto.awaitingConfirmationEstimated());
    }

    @Test
    void adminOverview_aggregatesRevenueQueuesAndMonthlySeries() {
        when(chargeRepository.sumAmountByStatus(CommissionChargeStatus.PAID))
                .thenReturn(new BigDecimal("300.00"));
        when(chargeRepository.countByStatus(CommissionChargeStatus.PAID)).thenReturn(3L);
        when(chargeRepository.sumAmountByStatus(CommissionChargeStatus.PENDING))
                .thenReturn(new BigDecimal("10.00"));
        when(chargeRepository.sumAmountByStatus(CommissionChargeStatus.PROCESSING))
                .thenReturn(new BigDecimal("20.00"));
        when(chargeRepository.sumAmountByStatus(CommissionChargeStatus.FAILED))
                .thenReturn(new BigDecimal("5.00"));
        when(chargeRepository.countByStatus(CommissionChargeStatus.PENDING)).thenReturn(1L);
        when(chargeRepository.countByStatus(CommissionChargeStatus.PROCESSING)).thenReturn(1L);
        when(chargeRepository.countByStatus(CommissionChargeStatus.FAILED)).thenReturn(2L);

        when(billingProfileRepository.countByPaymentBlockedTrue()).thenReturn(2L);
        when(confirmationRepository.countByStatus(MatchConfirmationStatus.PENDING_ADMIN_REVIEW))
                .thenReturn(4L);
        when(invoiceRepository.countByStatusIn(anyList())).thenReturn(6L);
        when(invoiceRepository.countByStatus(NfseInvoiceStatus.ISSUED)).thenReturn(7L);

        CommissionPolicy policy = new CommissionPolicy();
        policy.setPercentage(new BigDecimal("10.00"));
        when(commissionService.getPolicy()).thenReturn(policy);

        when(chargeRepository.findMonthlyPaidAmounts(any())).thenReturn(List.<Object[]>of(
                new Object[]{2026, 8, new BigDecimal("300.00")}));

        AdminFinanceOverviewDTO dto = service.adminOverview();

        assertEquals(new BigDecimal("300.00"), dto.grossRevenue());
        assertEquals(3L, dto.paidCount());
        assertEquals(new BigDecimal("35.00"), dto.pendingRevenue());
        assertEquals(4L, dto.pendingCount());
        assertEquals(2L, dto.failedChargeCount());
        assertEquals(2L, dto.blockedCompaniesCount());
        assertEquals(4L, dto.pendingReconciliationCount());
        assertEquals(6L, dto.pendingNfseCount());
        assertEquals(7L, dto.issuedNfseCount());
        assertEquals(new BigDecimal("10.00"), dto.commissionPercentage());
        assertEquals(1, dto.monthlyRevenue().size());
        assertTrue(dto.monthlyRevenue().get(0).label().endsWith("/2026"));
        assertEquals(new BigDecimal("300.00"), dto.monthlyRevenue().get(0).value());
    }

    @Test
    void adminOverview_includesPortalSubscriptionRevenueAsSeparateLine() {
        when(chargeRepository.sumAmountByStatus(any())).thenReturn(BigDecimal.ZERO);
        when(chargeRepository.findMonthlyPaidAmounts(any())).thenReturn(List.of());
        CommissionPolicy policy = new CommissionPolicy();
        policy.setPercentage(new BigDecimal("10.00"));
        when(commissionService.getPolicy()).thenReturn(policy);

        when(portalChargeRepository.sumAmountByStatus(
                com.main.nexus.model.enums.PortalSubscriptionChargeStatus.PAID))
                .thenReturn(new BigDecimal("999.50"));
        when(portalChargeRepository.countByStatus(
                com.main.nexus.model.enums.PortalSubscriptionChargeStatus.PAID)).thenReturn(5L);
        when(portalChargeRepository.sumAmountByStatusIn(anyList()))
                .thenReturn(new BigDecimal("199.90"));
        when(portalChargeRepository.countByStatusIn(anyList())).thenReturn(1L);

        AdminFinanceOverviewDTO dto = service.adminOverview();

        assertEquals(new BigDecimal("999.50"), dto.portalGrossRevenue());
        assertEquals(5L, dto.portalPaidCount());
        assertEquals(new BigDecimal("199.90"), dto.portalPendingRevenue());
        assertEquals(1L, dto.portalPendingCount());
        // A comissão continua contada à parte.
        assertEquals(BigDecimal.ZERO, dto.grossRevenue());
    }
}
