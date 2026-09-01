package com.main.nexus.service;

import com.main.nexus.dto.AdminCompanyConfirmationOverviewDTO;
import com.main.nexus.dto.AdminConfirmationQueueItemDTO;
import com.main.nexus.dto.AdminMatchConfirmationDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchConfirmationPendingReason;
import com.main.nexus.model.enums.MatchConfirmationResolution;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.MatchConfirmationRepository;
import com.main.nexus.repository.MatchStatusCheckRepository;
import com.main.nexus.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Supervisão do Admin sobre as confirmações pós-contratação.
//  - Prompt 2: leitura + triagem leve (marcar revisado, flag de observação).
//  - Prompt 3: reconciliação manual dos casos PENDING_ADMIN_REVIEW -- definir o
//    valor final a mão (vira definitivo p/ comissão) ou marcar "não foi possível
//    confirmar" (CLOSED_UNRESOLVED: sem valor, sem comissão, explicitamente).
@Service
public class MatchConfirmationAdminService {

    // Limiares do sinal de "empresa suspeita".
    private static final int CLOSED_NO_CHARGE_THRESHOLD = 3;
    private static final int DIVERGENCE_ABS_THRESHOLD = 3;
    private static final double DIVERGENCE_RATE_THRESHOLD = 0.40;

    @Autowired
    private MatchConfirmationRepository matchConfirmationRepository;

    @Autowired
    private MatchStatusCheckRepository matchStatusCheckRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommissionService commissionService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private NotificationService notificationService;

    // ─── Lista geral (fila completa, filtrável) ──────────────────────

    public List<AdminMatchConfirmationDTO> list(String status, Long companyId) {
        MatchConfirmationStatus parsed = parseStatus(status);
        return matchConfirmationRepository.findForAdmin(parsed, companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    // ─── Fila dedicada de reconciliação (Prompt 3) ─────────────────

    // Todos os casos PENDING_ADMIN_REVIEW, mais antigos (há mais dias pendentes) primeiro.
    public List<AdminMatchConfirmationDTO> pendingReconciliation() {
        return matchConfirmationRepository
                .findForAdmin(MatchConfirmationStatus.PENDING_ADMIN_REVIEW, null).stream()
                .map(this::toDTO)
                .sorted(Comparator.comparingLong(AdminMatchConfirmationDTO::daysPending).reversed())
                .toList();
    }

    // ─── Fila de atenção (por empresa) ──────────────────────────────

    public List<AdminConfirmationQueueItemDTO> queue() {
        Set<Long> companyIds = new LinkedHashSet<>(
                matchConfirmationRepository.findCompanyIdsWithConfirmations());
        companyRepository.findByUnderObservationTrue()
                .forEach(c -> companyIds.add(c.getId()));

        List<AdminConfirmationQueueItemDTO> items = new ArrayList<>();
        for (Long companyId : companyIds) {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company == null) continue;

            List<MatchConfirmation> all = matchConfirmationRepository
                    .findByMatchProjectCompanyIdOrderByOpenedAtDesc(companyId);
            Counts c = count(all);
            boolean suspicious = isSuspicious(c);
            boolean underObservation = Boolean.TRUE.equals(company.getUnderObservation());

            boolean needsAttention = suspicious || underObservation
                    || c.pendingReview > 0 || c.unreviewed > 0;
            if (!needsAttention) continue;

            items.add(new AdminConfirmationQueueItemDTO(
                    companyId,
                    company.getCompanyName(),
                    underObservation,
                    suspicious,
                    c.pendingReview,
                    c.closedNoCharge,
                    c.closedUnresolved,
                    c.valueDivergence,
                    c.noResponse,
                    c.completionDisagreement,
                    c.awaiting,
                    c.unreviewed));
        }
        // Suspeitas primeiro, depois quem tem mais casos em análise.
        items.sort((a, b) -> {
            if (a.suspicious() != b.suspicious()) return a.suspicious() ? -1 : 1;
            return Integer.compare(b.pendingReviewCount(), a.pendingReviewCount());
        });
        return items;
    }

    // ─── Drill-down por empresa ─────────────────────────────────────

    public AdminCompanyConfirmationOverviewDTO companyOverview(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Company not found: " + companyId));

        List<MatchConfirmation> all = matchConfirmationRepository
                .findByMatchProjectCompanyIdOrderByOpenedAtDesc(companyId);
        Counts c = count(all);

        return new AdminCompanyConfirmationOverviewDTO(
                companyId,
                company.getCompanyName(),
                Boolean.TRUE.equals(company.getUnderObservation()),
                isSuspicious(c),
                all.size(),
                c.awaiting,
                c.confirmed,
                c.pendingReview,
                c.closedNoCharge,
                c.closedUnresolved,
                c.valueDivergence,
                c.noResponse,
                c.completionDisagreement,
                c.unreviewed,
                all.stream().map(this::toDTO).toList());
    }

    // ─── Ações de triagem (Prompt 2) ───────────────────────────────

    @Transactional
    public AdminMatchConfirmationDTO markReviewed(Long matchId, Long adminUserId, String note) {
        MatchConfirmation confirmation = findConfirmation(matchId);
        confirmation.setAdminReviewed(true);
        confirmation.setReviewedAt(LocalDateTime.now());
        confirmation.setAdminNote(cleanNote(note));
        applyAdmin(confirmation, adminUserId);
        confirmation.setUpdatedAt(LocalDateTime.now());
        return toDTO(matchConfirmationRepository.save(confirmation));
    }

    @Transactional
    public AdminCompanyConfirmationOverviewDTO setObservation(Long companyId, boolean value) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Company not found: " + companyId));
        company.setUnderObservation(value);
        companyRepository.save(company);
        return companyOverview(companyId);
    }

    // ─── Reconciliação manual (Prompt 3) ──────────────────────────

    // O Admin define o valor final a mão (após contato com as partes). Vira
    // definitivo para comissão -- exatamente como um CONFIRMED automático.
    @Transactional
    public AdminMatchConfirmationDTO resolveWithValue(
            Long matchId, BigDecimal finalAmount, Long adminUserId, String note) {

        MatchConfirmation confirmation = findConfirmation(matchId);
        assertPending(confirmation);

        if (finalAmount == null || finalAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe um valor final maior que zero.");
        }

        LocalDateTime now = LocalDateTime.now();
        confirmation.setStatus(MatchConfirmationStatus.CONFIRMED);
        confirmation.setConfirmedAmount(finalAmount.setScale(2, RoundingMode.HALF_UP));
        confirmation.setResolution(MatchConfirmationResolution.ADMIN_SET_VALUE);
        confirmation.setAdminReviewed(true);
        confirmation.setReviewedAt(now);
        confirmation.setAdminNote(cleanNote(note));
        applyAdmin(confirmation, adminUserId);
        confirmation.setUpdatedAt(now);
        MatchConfirmation saved = matchConfirmationRepository.save(confirmation);

        // Camada financeira: conta a contratação (Prompt 1) e, fora das 3 gratuitas,
        // cria/dispara a cobrança da comissão (Prompt 5) -- igual ao CONFIRMED automático.
        commissionService.onHireConfirmed(saved);

        notifyBothSides(saved, true);
        return toDTO(saved);
    }

    // O Admin não conseguiu confirmar -> encerra SEM valor e SEM comissão,
    // explicitamente (CLOSED_UNRESOLVED). Nenhum valor é inventado.
    @Transactional
    public AdminMatchConfirmationDTO resolveUnconfirmable(
            Long matchId, Long adminUserId, String note) {

        MatchConfirmation confirmation = findConfirmation(matchId);
        assertPending(confirmation);

        LocalDateTime now = LocalDateTime.now();
        confirmation.setStatus(MatchConfirmationStatus.CLOSED_UNRESOLVED);
        confirmation.setConfirmedAmount(null);
        confirmation.setResolution(MatchConfirmationResolution.ADMIN_COULD_NOT_CONFIRM);
        confirmation.setAdminReviewed(true);
        confirmation.setReviewedAt(now);
        confirmation.setAdminNote(cleanNote(note));
        applyAdmin(confirmation, adminUserId);
        confirmation.setUpdatedAt(now);
        MatchConfirmation saved = matchConfirmationRepository.save(confirmation);

        notifyBothSides(saved, false);
        return toDTO(saved);
    }

    // ─── Internos ──────────────────────────────────────────────────

    private MatchConfirmation findConfirmation(Long matchId) {
        return matchConfirmationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No confirmation for match " + matchId));
    }

    private void assertPending(MatchConfirmation confirmation) {
        if (confirmation.getStatus() != MatchConfirmationStatus.PENDING_ADMIN_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este caso não está pendente de reconciliação.");
        }
    }

    private void applyAdmin(MatchConfirmation confirmation, Long adminUserId) {
        if (adminUserId != null) {
            userRepository.findById(adminUserId).ifPresent(confirmation::setReviewedByAdmin);
        }
    }

    private String cleanNote(String note) {
        return note != null && !note.isBlank() ? note.trim() : null;
    }

    private void notifyBothSides(MatchConfirmation confirmation, boolean valueSet) {
        Match match = confirmation.getMatch();
        User companyUser = match.getProject().getCompany().getUser();
        User professionalUser = match.getProfessional().getUser();
        String projectTitle = match.getProject().getTitle();

        if (valueSet) {
            notificationService.notifyConfirmationValueSetByAdmin(
                    companyUser, confirmation.getConfirmedAmount(), projectTitle, match.getId());
            notificationService.notifyConfirmationValueSetByAdmin(
                    professionalUser, confirmation.getConfirmedAmount(), projectTitle, match.getId());
        } else {
            notificationService.notifyConfirmationMarkedUnresolved(
                    companyUser, projectTitle, match.getId());
            notificationService.notifyConfirmationMarkedUnresolved(
                    professionalUser, projectTitle, match.getId());
        }
    }

    private static final class Counts {
        int awaiting, confirmed, pendingReview, closedNoCharge, closedUnresolved;
        int valueDivergence, noResponse, completionDisagreement;
        int unreviewed; // resolvido e ainda não marcado como revisado
        int resolved;   // tudo que saiu de AWAITING_RESPONSES
    }

    private Counts count(List<MatchConfirmation> all) {
        Counts c = new Counts();
        for (MatchConfirmation mc : all) {
            switch (mc.getStatus()) {
                case AWAITING_RESPONSES -> c.awaiting++;
                case CONFIRMED -> { c.confirmed++; c.resolved++; }
                case CLOSED_NO_CHARGE -> {
                    c.closedNoCharge++;
                    c.resolved++;
                    if (!mc.isAdminReviewed()) c.unreviewed++;
                }
                case CLOSED_UNRESOLVED -> {
                    c.closedUnresolved++;
                    c.resolved++;
                    if (!mc.isAdminReviewed()) c.unreviewed++;
                }
                case PENDING_ADMIN_REVIEW -> {
                    c.pendingReview++;
                    c.resolved++;
                    if (!mc.isAdminReviewed()) c.unreviewed++;
                    if (mc.getPendingReason() == MatchConfirmationPendingReason.VALUE_DIVERGENCE) {
                        c.valueDivergence++;
                    } else if (mc.getPendingReason() == MatchConfirmationPendingReason.NO_RESPONSE) {
                        c.noResponse++;
                    } else if (mc.getPendingReason() == MatchConfirmationPendingReason.COMPLETION_DISAGREEMENT) {
                        c.completionDisagreement++;
                    }
                }
            }
        }
        return c;
    }

    private boolean isSuspicious(Counts c) {
        if (c.closedNoCharge >= CLOSED_NO_CHARGE_THRESHOLD) return true;
        // Casos que não deu para reconciliar sozinho (divergência, sem resposta, ou
        // o Admin não conseguiu fechar) somam no mesmo balde.
        int unreconciled = c.valueDivergence + c.noResponse + c.closedUnresolved;
        if (unreconciled >= DIVERGENCE_ABS_THRESHOLD) return true;
        return c.resolved > 0
                && unreconciled >= DIVERGENCE_RATE_THRESHOLD * c.resolved;
    }

    private MatchConfirmationStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return MatchConfirmationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown confirmation status: " + status);
        }
    }

    private AdminMatchConfirmationDTO toDTO(MatchConfirmation mc) {
        Match match = mc.getMatch();
        Company company = match.getProject().getCompany();

        List<MatchStatusCheck> checks = matchStatusCheckRepository.findByMatchId(match.getId());
        Optional<MatchStatusCheck> companyCheck = checks.stream()
                .filter(x -> x.getAnsweredBy() == AuthorType.COMPANY).findFirst();
        Optional<MatchStatusCheck> proCheck = checks.stream()
                .filter(x -> x.getAnsweredBy() == AuthorType.PROFESSIONAL).findFirst();

        long daysPending = 0;
        if (mc.getStatus() == MatchConfirmationStatus.PENDING_ADMIN_REVIEW) {
            LocalDateTime since = mc.getResolvedAt() != null ? mc.getResolvedAt() : mc.getOpenedAt();
            daysPending = Math.max(0, ChronoUnit.DAYS.between(since, LocalDateTime.now()));
        }

        com.main.nexus.model.CommissionCharge charge =
                billingService.findChargeByConfirmation(mc.getId());

        return new AdminMatchConfirmationDTO(
                match.getId(),
                company.getId(),
                company.getCompanyName(),
                match.getProfessional().getId(),
                match.getProfessional().getName(),
                match.getProject().getTitle(),
                match.getProject().getOpportunityType() != null
                        ? match.getProject().getOpportunityType().name() : null,
                mc.getStatus().name(),
                mc.getPendingReason() != null ? mc.getPendingReason().name() : null,
                mc.getResolution() != null ? mc.getResolution().name() : null,
                mc.getOpenedAt(),
                mc.getDeadline(),
                mc.getResolvedAt(),
                daysPending,
                mc.getSuggestedAmount(),
                mc.getConfirmedAmount(),
                companyCheck.map(x -> x.getOutcome().name()).orElse(null),
                companyCheck.map(MatchStatusCheck::getFinalAmount).orElse(null),
                proCheck.map(x -> x.getOutcome().name()).orElse(null),
                proCheck.map(MatchStatusCheck::getFinalAmount).orElse(null),
                companyCheck.isPresent(),
                proCheck.isPresent(),
                mc.isAdminReviewed(),
                mc.getReviewedByAdmin() != null ? mc.getReviewedByAdmin().getEmail() : null,
                mc.getReviewedAt(),
                mc.getAdminNote(),
                charge != null ? charge.getStatus().name() : null,
                charge != null ? charge.getAmount() : null);
    }
}
