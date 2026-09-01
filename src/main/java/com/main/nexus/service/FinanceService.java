package com.main.nexus.service;

import com.main.nexus.dto.AdminFinanceOverviewDTO;
import com.main.nexus.dto.AwaitingConfirmationDTO;
import com.main.nexus.dto.ContractorCommissionStatusDTO;
import com.main.nexus.dto.ContractorFinanceOverviewDTO;
import com.main.nexus.dto.MonthlyAmountDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyBillingProfile;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.enums.CommissionChargeStatus;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import com.main.nexus.model.enums.NfseInvoiceStatus;
import com.main.nexus.model.enums.PortalSubscriptionChargeStatus;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyBillingProfileRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.MatchConfirmationRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.PortalSubscriptionChargeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Camada financeira -- Prompt 7: painel consolidado (Admin e contratante). Só
// leitura/agregação sobre o que os Prompts 1-6 já produzem -- não altera estado.
@Service
public class FinanceService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Locale PT_BR = new Locale("pt", "BR");

    private static final List<CommissionChargeStatus> OPEN_CHARGES = List.of(
            CommissionChargeStatus.PENDING,
            CommissionChargeStatus.PROCESSING,
            CommissionChargeStatus.FAILED);

    private static final List<PortalSubscriptionChargeStatus> OPEN_PORTAL_CHARGES = List.of(
            PortalSubscriptionChargeStatus.PENDING,
            PortalSubscriptionChargeStatus.PROCESSING,
            PortalSubscriptionChargeStatus.FAILED);

    @Autowired
    private CommissionChargeRepository chargeRepository;

    @Autowired
    private NfseInvoiceRepository invoiceRepository;

    @Autowired
    private MatchConfirmationRepository confirmationRepository;

    @Autowired
    private CompanyBillingProfileRepository billingProfileRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private CommissionService commissionService;

    @Autowired
    private PortalSubscriptionChargeRepository portalChargeRepository;

    @Autowired
    private CustomPortalRepository customPortalRepository;

    // ─── Contratante ─────────────────────────────────────────────

    @Transactional
    public ContractorFinanceOverviewDTO contractorOverview(Company company) {
        Long cid = company.getId();

        BigDecimal totalPaid = chargeRepository.sumAmountByCompanyAndStatus(
                cid, CommissionChargeStatus.PAID);
        long paidCount = chargeRepository.countByCompanyIdAndStatus(
                cid, CommissionChargeStatus.PAID);
        BigDecimal totalPending = chargeRepository.sumAmountByCompanyAndStatusIn(cid, OPEN_CHARGES);
        long pendingCount = chargeRepository.countByCompanyIdAndStatusIn(cid, OPEN_CHARGES);

        ContractorCommissionStatusDTO cs = commissionService.getContractorStatus(company);
        BigDecimal pct = cs.currentPercentage();

        List<MatchConfirmation> awaiting = confirmationRepository
                .findByMatchProjectCompanyIdAndStatusOrderByOpenedAtAsc(
                        cid, MatchConfirmationStatus.AWAITING_RESPONSES);
        List<AwaitingConfirmationDTO> awaitingDtos = new ArrayList<>();
        BigDecimal awaitingEstimated = BigDecimal.ZERO;
        for (MatchConfirmation mc : awaiting) {
            BigDecimal base = mc.getSuggestedAmount();
            BigDecimal est = base == null ? null
                    : base.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (est != null) {
                awaitingEstimated = awaitingEstimated.add(est);
            }
            awaitingDtos.add(new AwaitingConfirmationDTO(
                    mc.getMatch().getId(),
                    mc.getMatch().getProject().getTitle(),
                    mc.getMatch().getProfessional().getName(),
                    mc.getOpenedAt(),
                    mc.getDeadline(),
                    base,
                    est));
        }

        CompanyBillingProfile profile = billingProfileRepository.findByCompanyId(cid).orElse(null);
        boolean blocked = profile != null && Boolean.TRUE.equals(profile.getPaymentBlocked());

        long invoicesIssued = invoiceRepository.countByCompanyIdAndStatus(
                cid, NfseInvoiceStatus.ISSUED);
        long invoicesPending = invoiceRepository.countByCompanyIdAndStatusIn(
                cid, List.of(NfseInvoiceStatus.PENDING,
                        NfseInvoiceStatus.PROCESSING,
                        NfseInvoiceStatus.FAILED));

        // Mensalidades da plataforma personalizada (origem separada).
        boolean hasPortal = customPortalRepository.existsByCompanyId(cid);
        BigDecimal portalPaid = portalChargeRepository.sumAmountByCompanyAndStatus(
                cid, PortalSubscriptionChargeStatus.PAID);
        long portalPaidCount = portalChargeRepository.countByCompanyIdAndStatus(
                cid, PortalSubscriptionChargeStatus.PAID);
        BigDecimal portalPending = portalChargeRepository.sumAmountByCompanyAndStatusIn(
                cid, OPEN_PORTAL_CHARGES);
        long portalPendingCount = portalChargeRepository.countByCompanyIdAndStatusIn(
                cid, OPEN_PORTAL_CHARGES);

        return new ContractorFinanceOverviewDTO(
                billingService.isBillingEnabled(),
                billingService.isSimulated(),
                totalPaid,
                paidCount,
                totalPending,
                pendingCount,
                blocked,
                blocked
                        ? "Há uma comissão pendente de pagamento. Regularize para voltar a fechar contratações."
                        : null,
                awaitingDtos.size(),
                awaitingEstimated,
                cs.freeHiresLimit(),
                cs.usedFreeHires(),
                cs.freeHiresRemaining(),
                cs.commissionApplies(),
                pct,
                invoicesIssued,
                invoicesPending,
                awaitingDtos,
                hasPortal,
                portalPaid,
                portalPaidCount,
                portalPending,
                portalPendingCount);
    }

    // ─── Admin ───────────────────────────────────────────────────

    @Transactional
    public AdminFinanceOverviewDTO adminOverview() {
        BigDecimal gross = chargeRepository.sumAmountByStatus(CommissionChargeStatus.PAID);
        long paidCount = chargeRepository.countByStatus(CommissionChargeStatus.PAID);

        BigDecimal pendingRevenue = BigDecimal.ZERO;
        long pendingCount = 0;
        for (CommissionChargeStatus s : OPEN_CHARGES) {
            pendingRevenue = pendingRevenue.add(chargeRepository.sumAmountByStatus(s));
            pendingCount += chargeRepository.countByStatus(s);
        }
        long failedCharges = chargeRepository.countByStatus(CommissionChargeStatus.FAILED);
        long blockedCompanies = billingProfileRepository.countByPaymentBlockedTrue();

        long pendingReconciliation = confirmationRepository.countByStatus(
                MatchConfirmationStatus.PENDING_ADMIN_REVIEW);
        long pendingNfse = invoiceRepository.countByStatusIn(
                List.of(NfseInvoiceStatus.PENDING,
                        NfseInvoiceStatus.PROCESSING,
                        NfseInvoiceStatus.FAILED));
        long issuedNfse = invoiceRepository.countByStatus(NfseInvoiceStatus.ISSUED);

        // Mensalidades da plataforma personalizada (origem separada da comissao).
        BigDecimal portalGross = portalChargeRepository.sumAmountByStatus(
                PortalSubscriptionChargeStatus.PAID);
        long portalPaidCount = portalChargeRepository.countByStatus(
                PortalSubscriptionChargeStatus.PAID);
        BigDecimal portalPending = portalChargeRepository.sumAmountByStatusIn(OPEN_PORTAL_CHARGES);
        long portalPendingCount = portalChargeRepository.countByStatusIn(OPEN_PORTAL_CHARGES);

        return new AdminFinanceOverviewDTO(
                billingService.isLive(),
                billingService.isSimulated(),
                gross,
                paidCount,
                pendingRevenue,
                pendingCount,
                failedCharges,
                blockedCompanies,
                pendingReconciliation,
                pendingNfse,
                issuedNfse,
                commissionService.getPolicy().getPercentage(),
                CommissionService.FREE_HIRES_LIMIT,
                buildMonthlyRevenue(),
                portalGross,
                portalPaidCount,
                portalPending,
                portalPendingCount);
    }

    private List<MonthlyAmountDTO> buildMonthlyRevenue() {
        LocalDateTime since = LocalDateTime.now().minusMonths(12).withDayOfMonth(1)
                .toLocalDate().atStartOfDay();
        List<MonthlyAmountDTO> out = new ArrayList<>();
        for (Object[] row : chargeRepository.findMonthlyPaidAmounts(since)) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal sum = row[2] == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString());
            String label = Month.of(month).getDisplayName(TextStyle.SHORT, PT_BR) + "/" + year;
            out.add(new MonthlyAmountDTO(label, sum));
        }
        return out;
    }
}
