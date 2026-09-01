package com.main.nexus.service;

import com.main.nexus.dto.BillingModeDTO;
import com.main.nexus.dto.PortalSubscriptionChargeDTO;
import com.main.nexus.dto.PortalSubscriptionStatusDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.CustomPortalStatusHistory;
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
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Cobranca automatica da assinatura da plataforma personalizada.
//
// Modo real: o Mercado Pago cobra o cartao via assinatura recorrente
// (/preapproval) no ciclo mensal e avisa por webhook; aqui so criamos/atualizamos
// a linha PortalSubscriptionCharge e reconciliamos. Modo simulado: um job mensal
// cria a cobranca PROCESSING no vencimento e o Admin decide o resultado em
// /admin/portal-subscription-charges (simulateOutcome).
//
// Cobranca recusada -> paymentStatus OVERDUE + carencia de 7 dias; passada a
// carencia sem pagar, o job diario suspende o portal (fora do ar). Um pagamento
// bem-sucedido reativa. Nada disso mexe no bloqueio de fechar contratacoes.
@Service
public class PortalSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PortalSubscriptionService.class);

    static final int PAYMENT_GRACE_DAYS = 7;

    private static final List<PortalSubscriptionChargeStatus> OPEN = List.of(
            PortalSubscriptionChargeStatus.PENDING,
            PortalSubscriptionChargeStatus.PROCESSING,
            PortalSubscriptionChargeStatus.FAILED);

    private static final List<PortalSubscriptionChargeStatus> COVERS_CYCLE = List.of(
            PortalSubscriptionChargeStatus.PENDING,
            PortalSubscriptionChargeStatus.PROCESSING,
            PortalSubscriptionChargeStatus.FAILED,
            PortalSubscriptionChargeStatus.PAID);

    @Value("${nexus.billing.enabled}")
    private boolean billingEnabled;

    @Value("${nexus.billing.simulate}")
    private boolean simulate;

    @Autowired
    private MercadoPagoClient mercadoPago;

    @Autowired
    private CustomPortalRepository customPortalRepository;

    @Autowired
    private CustomPortalStatusHistoryRepository historyRepository;

    @Autowired
    private PortalSubscriptionChargeRepository chargeRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ─── Modo ────────────────────────────────────────────────────

    public boolean isLive() {
        return billingEnabled && mercadoPago.hasCredentials();
    }

    public boolean isSimulated() {
        return simulate && !isLive();
    }

    public boolean isBillingEnabled() {
        return isLive() || isSimulated();
    }

    public BillingModeDTO mode() {
        return new BillingModeDTO(isLive(), isSimulated());
    }

    // ─── Status / cartao (contratante) ──────────────────────────

    @Transactional(readOnly = true)
    public PortalSubscriptionStatusDTO getStatus(Company company) {
        CustomPortal portal = customPortalRepository.findByCompanyId(company.getId()).orElse(null);
        String publicKey = isLive() ? mercadoPago.publicKey() : "";
        if (portal == null) {
            return new PortalSubscriptionStatusDTO(false, isBillingEnabled(), isSimulated(),
                    publicKey, null, null, null, null, null, null, false, null, null);
        }
        return new PortalSubscriptionStatusDTO(
                true,
                isBillingEnabled(),
                isSimulated(),
                publicKey,
                portal.getStatus().name(),
                portal.getPaymentStatus().name(),
                portal.getPlanName(),
                portal.getPlanPrice(),
                portal.getNextDueDate(),
                portal.getPaymentGraceUntil(),
                portal.hasSubscriptionCard(),
                portal.getSubscriptionCardBrand(),
                portal.getSubscriptionCardLast4());
    }

    @Transactional
    public PortalSubscriptionStatusDTO saveCard(Company company, String cardToken) {
        if (!isBillingEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A cobrança de plataforma não está habilitada nesta instância.");
        }
        if (cardToken == null || cardToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card token is required.");
        }
        CustomPortal portal = customPortalRepository.findByCompanyId(company.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Nenhuma plataforma personalizada para este contratante."));
        if (portal.getStatus() == CustomPortalStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta plataforma foi descontinuada.");
        }

        if (isSimulated()) {
            portal.setMpPreapprovalId("SIM-" + portal.getId());
            portal.setSubscriptionCardBrand("Simulado");
            portal.setSubscriptionCardLast4("0000");
        } else {
            String email = company.getUser() != null ? company.getUser().getEmail() : null;
            if (email == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O contratante não tem e-mail cadastrado.");
            }
            MercadoPagoClient.CardTokenInfo info = mercadoPago.getCardToken(cardToken);
            if (portal.getMpPreapprovalId() == null) {
                MercadoPagoClient.Preapproval pre = mercadoPago.createPreapproval(
                        portal.getPlanPrice(), cardToken, email, portal.getNextDueDate(),
                        "Assinatura Plataforma Nexus — " + portal.getSubdomain(),
                        "portal-sub-" + portal.getId());
                portal.setMpPreapprovalId(pre.id());
            } else {
                mercadoPago.updatePreapprovalCard(portal.getMpPreapprovalId(), cardToken);
            }
            portal.setSubscriptionCardBrand(info.paymentMethodName());
            portal.setSubscriptionCardLast4(info.lastFourDigits());
        }

        // Novo cartao -> novo prazo de carencia; retenta a mensalidade em aberto.
        if (portal.getPaymentStatus() == CustomPortalPaymentStatus.OVERDUE) {
            portal.setPaymentGraceUntil(LocalDate.now().plusDays(PAYMENT_GRACE_DAYS));
        }
        portal.setUpdatedAt(LocalDateTime.now());
        customPortalRepository.save(portal);

        retryOpenCharge(portal);
        return getStatus(company);
    }

    // Depois que o contratante troca o cartao, recoloca a mensalidade em aberto
    // para nova tentativa e renova a carencia. No modo real NAO re-consultamos o
    // pagamento antigo (ele fica recusado para sempre) -- o Mercado Pago retenta a
    // assinatura sozinho com o novo cartao e o webhook resolve; limpamos o
    // mpPaymentId para o job de varredura nao ficar re-checando o pagamento morto.
    private void retryOpenCharge(CustomPortal portal) {
        PortalSubscriptionCharge c = chargeRepository
                .findFirstByCustomPortalIdAndStatusInOrderByCreatedAtDesc(portal.getId(), OPEN)
                .orElse(null);
        if (c == null || c.getStatus() == PortalSubscriptionChargeStatus.PAID
                || c.getStatus() == PortalSubscriptionChargeStatus.CANCELED) {
            return;
        }
        c.setStatus(PortalSubscriptionChargeStatus.PROCESSING);
        c.setFailureReason(null);
        c.setAttempts(c.getAttempts() + 1);
        c.setLastAttemptAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        if (isLive()) {
            c.setMpPaymentId(null);
        }
        chargeRepository.save(c);
    }

    // ─── Reconciliacao ─────────────────────────────────────────

    private void reconcile(PortalSubscriptionCharge c, String status, String detail, String paymentId) {
        c.setMpPaymentId(paymentId);
        c.setMpStatusDetail(detail);
        c.setUpdatedAt(LocalDateTime.now());
        CustomPortal portal = c.getCustomPortal();
        User companyUser = portal.getCompany().getUser();

        if ("approved".equals(status)) {
            c.setStatus(PortalSubscriptionChargeStatus.PAID);
            c.setPaidAt(LocalDateTime.now());
            c.setFailureReason(null);
            chargeRepository.save(c);

            portal.setPaymentStatus(CustomPortalPaymentStatus.UP_TO_DATE);
            portal.setPaymentGraceUntil(null);
            portal.setNextDueDate(c.getDueDate().plusMonths(1));
            portal.setLastRenewalReminderFor(null);
            boolean wasSuspendedForNonPayment = portal.getStatus() == CustomPortalStatus.SUSPENDED;
            if (wasSuspendedForNonPayment) {
                changeStatus(portal, CustomPortalStatus.ACTIVE,
                        "Reativada automaticamente após o pagamento da mensalidade.");
                resumePreapproval(portal);
            }
            portal.setUpdatedAt(LocalDateTime.now());
            customPortalRepository.save(portal);

            if (companyUser != null) {
                notificationService.notifyPortalSubscriptionCharged(companyUser, c.getAmount());
                if (wasSuspendedForNonPayment) {
                    notificationService.notifyPortalReactivatedAfterPayment(companyUser);
                }
            }
            eventPublisher.publishEvent(new PortalSubscriptionChargePaidEvent(c.getId()));
        } else if ("rejected".equals(status) || "cancelled".equals(status)) {
            c.setStatus(PortalSubscriptionChargeStatus.FAILED);
            c.setFailureReason("Cartão recusado pelo Mercado Pago"
                    + (detail != null ? " (" + detail + ")" : "") + ".");
            chargeRepository.save(c);

            portal.setPaymentStatus(CustomPortalPaymentStatus.OVERDUE);
            if (portal.getPaymentGraceUntil() == null) {
                portal.setPaymentGraceUntil(LocalDate.now().plusDays(PAYMENT_GRACE_DAYS));
            }
            portal.setUpdatedAt(LocalDateTime.now());
            customPortalRepository.save(portal);

            if (companyUser != null) {
                notificationService.notifyPortalSubscriptionPaymentFailed(
                        companyUser, portal.getPaymentGraceUntil());
            }
        } else {
            c.setStatus(PortalSubscriptionChargeStatus.PROCESSING);
            chargeRepository.save(c);
        }
    }

    // ─── Webhook do Mercado Pago ───────────────────────────────

    // type=subscription_authorized_payment -> id do authorized_payment.
    @Transactional
    public void handleAuthorizedPaymentWebhook(String authorizedPaymentId) {
        if (authorizedPaymentId == null || authorizedPaymentId.isBlank() || !isLive()) return;
        MercadoPagoClient.AuthorizedPayment ap;
        try {
            ap = mercadoPago.getAuthorizedPayment(authorizedPaymentId);
        } catch (Exception e) {
            log.warn("Webhook assinatura: falha ao consultar authorized_payment {}: {}",
                    authorizedPaymentId, e.getMessage());
            return;
        }
        CustomPortal portal = ap.preapprovalId() == null ? null
                : customPortalRepository.findByMpPreapprovalId(ap.preapprovalId()).orElse(null);
        if (portal == null) {
            log.info("Webhook assinatura para preapproval {} sem plataforma correspondente.",
                    ap.preapprovalId());
            return;
        }
        if (portal.getStatus() == CustomPortalStatus.CANCELED) {
            log.info("Webhook assinatura para plataforma {} já descontinuada -- ignorado.",
                    portal.getId());
            return;
        }
        PortalSubscriptionCharge c = resolveChargeForWebhook(portal, ap.paymentId());
        if (c.getStatus() == PortalSubscriptionChargeStatus.PAID
                || c.getStatus() == PortalSubscriptionChargeStatus.CANCELED) {
            return;
        }
        reconcile(c, ap.paymentStatus(), ap.paymentStatusDetail(),
                ap.paymentId() != null ? ap.paymentId() : c.getMpPaymentId());
    }

    // type=payment -> pode ser um pagamento recorrente da assinatura.
    @Transactional
    public void handlePaymentWebhook(String paymentId) {
        if (paymentId == null || paymentId.isBlank() || !isLive()) return;
        PortalSubscriptionCharge c = chargeRepository.findByMpPaymentId(paymentId).orElse(null);
        MercadoPagoClient.Payment p;
        try {
            p = mercadoPago.getPayment(paymentId);
        } catch (Exception e) {
            log.warn("Webhook pagamento {}: falha ao consultar o MP: {}", paymentId, e.getMessage());
            return;
        }
        if (c == null) {
            String ref = p.externalReference();
            if (ref == null || !ref.startsWith("portal-sub-")) {
                return; // nao e da assinatura de plataforma
            }
            Long portalId = parseId(ref, "portal-sub-");
            CustomPortal portal = portalId == null ? null
                    : customPortalRepository.findById(portalId).orElse(null);
            if (portal == null || portal.getStatus() == CustomPortalStatus.CANCELED) return;
            // Nao criamos cobranca a partir de um webhook 'payment' solto -- isso e
            // papel do 'subscription_authorized_payment'. So reconciliamos uma que
            // ja exista em aberto para esta plataforma.
            c = chargeRepository
                    .findFirstByCustomPortalIdAndStatusInOrderByCreatedAtDesc(portal.getId(), OPEN)
                    .orElse(null);
            if (c == null) {
                log.info("Webhook pagamento {} da plataforma {} sem mensalidade em aberto -- ignorado.",
                        paymentId, portal.getId());
                return;
            }
        }
        if (c.getStatus() == PortalSubscriptionChargeStatus.PAID
                || c.getStatus() == PortalSubscriptionChargeStatus.CANCELED) {
            return;
        }
        reconcile(c, p.status(), p.statusDetail(), p.id());
    }

    private PortalSubscriptionCharge resolveChargeForWebhook(CustomPortal portal, String paymentId) {
        if (paymentId != null) {
            PortalSubscriptionCharge byPayment = chargeRepository.findByMpPaymentId(paymentId).orElse(null);
            if (byPayment != null) return byPayment;
        }
        return chargeRepository
                .findFirstByCustomPortalIdAndStatusInOrderByCreatedAtDesc(portal.getId(), OPEN)
                .orElseGet(() -> newCharge(portal, portal.getNextDueDate()));
    }

    // ─── Simulacao / fila do Admin ─────────────────────────────

    public List<PortalSubscriptionChargeDTO> listForAdmin(String status) {
        List<PortalSubscriptionCharge> rows;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            rows = chargeRepository.findByStatusInOrderByCreatedAtDesc(
                    List.of(PortalSubscriptionChargeStatus.values()));
        } else {
            rows = chargeRepository.findByStatusInOrderByCreatedAtDesc(List.of(parseStatus(status)));
        }
        return rows.stream().map(PortalSubscriptionChargeDTO::from).toList();
    }

    public List<PortalSubscriptionChargeDTO> chargesForCompany(Company company) {
        return chargeRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .map(PortalSubscriptionChargeDTO::from).toList();
    }

    @Transactional
    public PortalSubscriptionChargeDTO simulateOutcome(Long chargeId, String outcome) {
        if (!isSimulated()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A simulação de cobrança só está disponível no modo simulate.");
        }
        PortalSubscriptionCharge c = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found."));
        if (c.getStatus() != PortalSubscriptionChargeStatus.PROCESSING
                && c.getStatus() != PortalSubscriptionChargeStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cobrança já foi finalizada.");
        }
        boolean approved = "approved".equalsIgnoreCase(outcome);
        String paymentId = c.getMpPaymentId() != null ? c.getMpPaymentId() : "SIM-" + c.getId();
        reconcile(c, approved ? "approved" : "rejected", "simulado", paymentId);
        return PortalSubscriptionChargeDTO.from(c);
    }

    // ─── Job diario (NexusScheduler) ──────────────────────────

    @Transactional
    public void runBillingCycle() {
        if (!isBillingEnabled()) return;
        LocalDate today = LocalDate.now();

        // 1) Ciclo vencido de portal ACTIVE.
        for (CustomPortal portal : customPortalRepository
                .findByStatusAndNextDueDateLessThanEqual(CustomPortalStatus.ACTIVE, today)) {
            if (portal.getPaymentStatus() == CustomPortalPaymentStatus.CANCELED) {
                continue;
            }
            if (portal.getMpPreapprovalId() == null) {
                // Nunca configurou o pagamento -> tratado como inadimplencia:
                // inicia a carencia; passada, o passo (2) suspende.
                if (portal.getPaymentStatus() != CustomPortalPaymentStatus.OVERDUE) {
                    portal.setPaymentStatus(CustomPortalPaymentStatus.OVERDUE);
                    portal.setPaymentGraceUntil(today.plusDays(PAYMENT_GRACE_DAYS));
                    portal.setUpdatedAt(LocalDateTime.now());
                    customPortalRepository.save(portal);
                    if (portal.getCompany().getUser() != null) {
                        notificationService.notifyPortalSubscriptionPaymentFailed(
                                portal.getCompany().getUser(), portal.getPaymentGraceUntil());
                    }
                }
                continue;
            }
            // Tem assinatura: no modo simulado geramos a mensalidade do ciclo (o
            // Admin decide o resultado); no modo real o Mercado Pago cobra sozinho
            // e a linha nasce pelo webhook.
            if (isSimulated()) {
                boolean covered = chargeRepository.existsByCustomPortalIdAndDueDateAndStatusIn(
                        portal.getId(), portal.getNextDueDate(), COVERS_CYCLE);
                if (!covered) {
                    newCharge(portal, portal.getNextDueDate());
                }
            }
        }

        // 2) Carencia vencida -> suspende o portal (fora do ar).
        for (CustomPortal portal : customPortalRepository.findByPaymentStatusAndStatus(
                CustomPortalPaymentStatus.OVERDUE, CustomPortalStatus.ACTIVE)) {
            LocalDate grace = portal.getPaymentGraceUntil();
            if (grace != null && grace.isBefore(today)) {
                changeStatus(portal, CustomPortalStatus.SUSPENDED,
                        "Suspensa automaticamente por falta de pagamento da mensalidade.");
                portal.setUpdatedAt(LocalDateTime.now());
                customPortalRepository.save(portal);
                pausePreapproval(portal);
                if (portal.getCompany().getUser() != null) {
                    notificationService.notifyPortalSuspendedForNonPayment(portal.getCompany().getUser());
                }
            }
        }

        // 3) Modo real: re-consulta cobrancas PROCESSING presas (webhook perdido).
        if (isLive()) {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
            for (PortalSubscriptionCharge c : chargeRepository.findStaleProcessing(threshold)) {
                try {
                    MercadoPagoClient.Payment p = mercadoPago.getPayment(c.getMpPaymentId());
                    reconcile(c, p.status(), p.statusDetail(), p.id());
                } catch (Exception e) {
                    log.warn("Re-consulta da mensalidade {} falhou: {}", c.getId(), e.getMessage());
                }
            }
        }
    }

    // ─── Ganchos chamados pelo CustomPortalService ─────────────
    // Best-effort: uma falha no MP nao pode quebrar a acao do Admin.

    public void onPortalSuspended(CustomPortal portal) {
        pausePreapproval(portal);
    }

    public void onPortalReactivated(CustomPortal portal) {
        resumePreapproval(portal);
    }

    @Transactional
    public void onPortalCanceled(CustomPortal portal) {
        if (isLive() && portal.getMpPreapprovalId() != null
                && !portal.getMpPreapprovalId().startsWith("SIM-")) {
            try {
                mercadoPago.updatePreapprovalStatus(portal.getMpPreapprovalId(), "cancelled");
            } catch (Exception e) {
                log.warn("Falha ao cancelar a assinatura MP do portal {}: {}",
                        portal.getId(), e.getMessage());
            }
        }
        for (PortalSubscriptionCharge c : chargeRepository
                .findByCustomPortalIdAndStatusIn(portal.getId(), OPEN)) {
            c.setStatus(PortalSubscriptionChargeStatus.CANCELED);
            c.setUpdatedAt(LocalDateTime.now());
            chargeRepository.save(c);
        }
    }

    public void onSubscriptionAmountChanged(CustomPortal portal, BigDecimal previousPrice) {
        if (previousPrice != null && previousPrice.compareTo(portal.getPlanPrice()) == 0) return;
        if (isLive() && portal.getMpPreapprovalId() != null
                && !portal.getMpPreapprovalId().startsWith("SIM-")) {
            try {
                mercadoPago.updatePreapprovalAmount(portal.getMpPreapprovalId(), portal.getPlanPrice());
            } catch (Exception e) {
                log.warn("Falha ao atualizar o valor da assinatura MP do portal {}: {}",
                        portal.getId(), e.getMessage());
            }
        }
    }

    // ─── Internos ─────────────────────────────────────────────

    private PortalSubscriptionCharge newCharge(CustomPortal portal, LocalDate dueDate) {
        PortalSubscriptionCharge c = new PortalSubscriptionCharge();
        c.setCustomPortal(portal);
        c.setCompany(portal.getCompany());
        c.setAmount(portal.getPlanPrice());
        c.setDueDate(dueDate != null ? dueDate : LocalDate.now());
        c.setStatus(PortalSubscriptionChargeStatus.PROCESSING);
        c.setAttempts(1);
        c.setLastAttemptAt(LocalDateTime.now());
        return chargeRepository.save(c);
    }

    private void changeStatus(CustomPortal portal, CustomPortalStatus newStatus, String note) {
        CustomPortalStatus previous = portal.getStatus();
        if (previous == newStatus) return;
        portal.setStatus(newStatus);
        CustomPortalStatusHistory row = new CustomPortalStatusHistory();
        row.setCustomPortal(portal);
        row.setPreviousStatus(previous);
        row.setNewStatus(newStatus);
        row.setChangedBy(null); // transicao automatica
        row.setNote(note);
        historyRepository.save(row);
    }

    private void pausePreapproval(CustomPortal portal) {
        if (isLive() && portal.getMpPreapprovalId() != null
                && !portal.getMpPreapprovalId().startsWith("SIM-")) {
            try {
                mercadoPago.updatePreapprovalStatus(portal.getMpPreapprovalId(), "paused");
            } catch (Exception e) {
                log.warn("Falha ao pausar a assinatura MP do portal {}: {}",
                        portal.getId(), e.getMessage());
            }
        }
    }

    private void resumePreapproval(CustomPortal portal) {
        if (isLive() && portal.getMpPreapprovalId() != null
                && !portal.getMpPreapprovalId().startsWith("SIM-")) {
            try {
                mercadoPago.updatePreapprovalStatus(portal.getMpPreapprovalId(), "authorized");
            } catch (Exception e) {
                log.warn("Falha ao retomar a assinatura MP do portal {}: {}",
                        portal.getId(), e.getMessage());
            }
        }
    }

    private Long parseId(String ref, String prefix) {
        try {
            return Long.parseLong(ref.substring(prefix.length()));
        } catch (Exception e) {
            return null;
        }
    }

    private PortalSubscriptionChargeStatus parseStatus(String s) {
        try {
            return PortalSubscriptionChargeStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown charge status: " + s);
        }
    }
}
