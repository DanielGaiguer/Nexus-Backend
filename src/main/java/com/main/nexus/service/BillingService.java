package com.main.nexus.service;

import com.main.nexus.dto.BillingConfigDTO;
import com.main.nexus.dto.BillingStatusDTO;
import com.main.nexus.dto.CommissionChargeDTO;
import com.main.nexus.model.CommissionCharge;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyBillingProfile;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CommissionChargeStatus;
import com.main.nexus.model.enums.PaymentBlockReason;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyBillingProfileRepository;
import com.main.nexus.repository.CompanyRepository;
import java.math.BigDecimal;
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

// Camada financeira -- Prompt 5: cartao salvo e cobranca automatica da comissao
// via Mercado Pago.
//
// Fluxo: contratacao CONFIRMED e fora das 3 gratuitas -> CommissionService cria
// um CommissionCharge(PENDING) e publica um evento -> BillingEventListener chama
// processCharge() apos o commit -> gera token do cartao salvo + cria pagamento no
// MP -> reconcilia (aprovado/recusado/em processo). O webhook e o job de
// varredura cobrem os pagamentos assincronos. Se a cobranca falha (ou o
// contratante nao tem cartao), o contratante fica BLOQUEADO de fechar novas
// contratacoes ate regularizar (assertCanCloseNewHire, chamado no funil de match).
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private static final List<CommissionChargeStatus> UNPAID =
            List.of(CommissionChargeStatus.PENDING,
                    CommissionChargeStatus.PROCESSING,
                    CommissionChargeStatus.FAILED);

    @Value("${nexus.billing.enabled}")
    private boolean billingEnabled;

    @Value("${nexus.billing.simulate}")
    private boolean simulate;

    @Autowired
    private MercadoPagoClient mercadoPago;

    @Autowired
    private CompanyBillingProfileRepository profileRepository;

    @Autowired
    private CommissionChargeRepository chargeRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private NotificationService notificationService;

    // Dispara a emissão da NFS-e (Prompt 6) quando uma cobrança vira PAID, via
    // CommissionChargePaidEvent consumido APÓS o commit (NfseEventListener) --
    // uma falha fiscal nunca desfaz a cobrança já confirmada.
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ─── Config / status ─────────────────────────────────────────

    // Mercado Pago de verdade (credenciais presentes).
    public boolean isLive() {
        return billingEnabled && mercadoPago.hasCredentials();
    }

    // Simulado: exercita o fluxo sem MP. Só quando não há credenciais reais.
    public boolean isSimulated() {
        return simulate && !isLive();
    }

    // "Billing ligado" = o resto do fluxo (criar cobrança, bloqueio) engata.
    public boolean isBillingEnabled() {
        return isLive() || isSimulated();
    }

    public BillingConfigDTO getConfig() {
        return new BillingConfigDTO(
                isBillingEnabled(),
                isLive() ? mercadoPago.publicKey() : "",
                isSimulated());
    }

    public com.main.nexus.dto.BillingModeDTO mode() {
        return new com.main.nexus.dto.BillingModeDTO(isLive(), isSimulated());
    }

    @Transactional
    public CompanyBillingProfile profileFor(Company company) {
        return profileRepository.findByCompanyId(company.getId())
                .orElseGet(() -> {
                    CompanyBillingProfile p = new CompanyBillingProfile();
                    p.setCompany(company);
                    return profileRepository.save(p);
                });
    }

    @Transactional
    public BillingStatusDTO getStatus(Company company) {
        CompanyBillingProfile p = profileFor(company);
        CommissionCharge pending = chargeRepository
                .findFirstByCompanyIdAndStatusInOrderByCreatedAtDesc(company.getId(), UNPAID)
                .orElse(null);
        return toStatusDTO(p, pending);
    }

    // ─── Cartao ──────────────────────────────────────────────────

    @Transactional
    public BillingStatusDTO saveCard(Company company, String cardToken) {
        requireEnabled();
        if (cardToken == null || cardToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card token is required.");
        }
        CompanyBillingProfile p = profileFor(company);

        // Modo simulado: cartão de teste sem Mercado Pago.
        if (isSimulated()) {
            p.setMpCustomerId("SIM-CUSTOMER");
            p.setMpCardId("SIM-CARD");
            p.setCardBrand("Simulado");
            p.setCardLast4("0000");
            p.setCardExpMonth(12);
            p.setCardExpYear(java.time.LocalDate.now().getYear() + 3);
            p.setCardholderName("Cartão de teste");
            p.setUpdatedAt(LocalDateTime.now());
            profileRepository.save(p);
            if (Boolean.TRUE.equals(p.getPaymentBlocked())) {
                retryBlockingChargeInternal(company);
            }
            return getStatus(company);
        }

        String email = company.getUser() != null ? company.getUser().getEmail() : null;
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company has no e-mail on file.");
        }
        if (p.getMpCustomerId() == null) {
            p.setMpCustomerId(mercadoPago.getOrCreateCustomer(email, company.getCompanyName()));
        }
        // Substitui o cartao anterior, se houver.
        if (p.getMpCardId() != null) {
            mercadoPago.deleteCard(p.getMpCustomerId(), p.getMpCardId());
        }

        MercadoPagoClient.SavedCard card = mercadoPago.saveCard(p.getMpCustomerId(), cardToken);
        p.setMpCardId(card.id());
        p.setCardBrand(card.paymentMethodName() != null ? card.paymentMethodName() : card.paymentMethodId());
        p.setCardLast4(card.lastFourDigits());
        p.setCardExpMonth(card.expirationMonth());
        p.setCardExpYear(card.expirationYear());
        p.setCardholderName(card.cardholderName());
        p.setUpdatedAt(LocalDateTime.now());
        profileRepository.save(p);

        // Se estava bloqueado por falta de cartao / cobranca recusada, tenta de novo agora.
        if (Boolean.TRUE.equals(p.getPaymentBlocked())) {
            retryBlockingChargeInternal(company);
        }
        return getStatus(company);
    }

    @Transactional
    public BillingStatusDTO removeCard(Company company) {
        CompanyBillingProfile p = profileFor(company);
        if (p.getMpCustomerId() != null && p.getMpCardId() != null) {
            mercadoPago.deleteCard(p.getMpCustomerId(), p.getMpCardId());
        }
        p.setMpCardId(null);
        p.setCardBrand(null);
        p.setCardLast4(null);
        p.setCardExpMonth(null);
        p.setCardExpYear(null);
        p.setCardholderName(null);
        p.setUpdatedAt(LocalDateTime.now());

        // Se ha cobranca em aberto, remover o cartao bloqueia.
        if (chargeRepository.existsByCompanyIdAndStatusIn(company.getId(), UNPAID)) {
            block(p, PaymentBlockReason.NO_CARD_ON_FILE,
                    "Você removeu o cartão e há uma comissão pendente de cobrança.");
        }
        profileRepository.save(p);
        return getStatus(company);
    }

    // ─── Bloqueio ────────────────────────────────────────────────

    // Chamado no funil de fechamento de match (MatchService.incrementFilledPositions).
    public void assertCanCloseNewHire(Company company) {
        CompanyBillingProfile p = profileRepository.findByCompanyId(company.getId()).orElse(null);
        if (p != null && Boolean.TRUE.equals(p.getPaymentBlocked())) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    blockMessage(p.getBlockReason())
                    + " Regularize em Financeiro antes de fechar novas contratações.");
        }
    }

    private void block(CompanyBillingProfile p, PaymentBlockReason reason, String extra) {
        p.setPaymentBlocked(true);
        p.setBlockReason(reason);
        p.setBlockedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        if (p.getCompany().getUser() != null) {
            notificationService.notifyCommissionChargeFailed(
                    p.getCompany().getUser(),
                    (extra != null ? extra + " " : "") + blockMessage(reason));
        }
    }

    private void unblock(CompanyBillingProfile p) {
        if (!Boolean.TRUE.equals(p.getPaymentBlocked())) return;
        p.setPaymentBlocked(false);
        p.setBlockReason(null);
        p.setBlockedAt(null);
        p.setUpdatedAt(LocalDateTime.now());
    }

    private String blockMessage(PaymentBlockReason reason) {
        if (reason == PaymentBlockReason.NO_CARD_ON_FILE) {
            return "Há uma comissão a cobrar e nenhum cartão cadastrado.";
        }
        return "A última cobrança de comissão foi recusada pelo cartão.";
    }

    // ─── Criacao da cobranca (chamado por CommissionService) ──────

    @Transactional
    public CommissionCharge createCharge(MatchConfirmation confirmation,
                                         BigDecimal baseAmount, BigDecimal percentage, BigDecimal amount) {
        return chargeRepository.findByMatchConfirmationId(confirmation.getId())
                .orElseGet(() -> {
                    CommissionCharge c = new CommissionCharge();
                    c.setMatchConfirmation(confirmation);
                    c.setCompany(confirmation.getMatch().getProject().getCompany());
                    c.setBaseAmount(baseAmount);
                    c.setPercentage(percentage);
                    c.setAmount(amount);
                    c.setStatus(CommissionChargeStatus.PENDING);
                    return chargeRepository.save(c);
                });
    }

    // ─── Processamento da cobranca ───────────────────────────────

    @Transactional
    public void processCharge(Long chargeId) {
        CommissionCharge charge = chargeRepository.findById(chargeId).orElse(null);
        if (charge == null) return;
        if (charge.getStatus() != CommissionChargeStatus.PENDING) {
            return; // ja em processamento / paga / cancelada
        }
        if (!isBillingEnabled()) {
            log.info("Billing desligado -- cobranca {} fica PENDING.", chargeId);
            return;
        }

        Company company = charge.getCompany();
        CompanyBillingProfile p = profileFor(company);

        if (!p.hasCard()) {
            charge.setFailureReason("Contratante sem cartão cadastrado.");
            charge.setUpdatedAt(LocalDateTime.now());
            chargeRepository.save(charge);
            block(p, PaymentBlockReason.NO_CARD_ON_FILE, null);
            profileRepository.save(p);
            return;
        }

        charge.setStatus(CommissionChargeStatus.PROCESSING);
        charge.setAttempts(charge.getAttempts() + 1);
        charge.setLastAttemptAt(LocalDateTime.now());
        charge.setUpdatedAt(LocalDateTime.now());

        // Modo simulado: fica PROCESSING; o Admin decide o resultado em
        // /admin/commission-charges (simulateOutcome). Sem nenhuma chamada ao MP.
        if (isSimulated()) {
            charge.setMpPaymentId("SIM-" + charge.getId());
            charge.setFailureReason(null);
            chargeRepository.save(charge);
            return;
        }

        chargeRepository.saveAndFlush(charge);

        try {
            String cardToken = mercadoPago.createCardTokenFromSavedCard(p.getMpCardId());
            String idempotencyKey = "commission-charge-" + charge.getId() + "-attempt-" + charge.getAttempts();
            MercadoPagoClient.Payment payment = mercadoPago.createPayment(
                    charge.getAmount(),
                    cardToken,
                    p.getMpCustomerId(),
                    "Comissão Nexus — contratação #" + charge.getMatchConfirmation().getMatch().getId(),
                    "commission-charge-" + charge.getId(),
                    idempotencyKey);
            reconcile(charge, payment.status(), payment.statusDetail(), payment.id());
        } catch (ResponseStatusException e) {
            // Erro transitorio de comunicacao com o MP -- volta para PENDING; o job
            // de varredura (ou uma nova confirmacao) tenta de novo.
            charge.setStatus(CommissionChargeStatus.PENDING);
            charge.setFailureReason("Erro temporário ao contatar o Mercado Pago. Nova tentativa em breve.");
            charge.setUpdatedAt(LocalDateTime.now());
            chargeRepository.save(charge);
            log.warn("Cobranca {} falhou de forma transitoria: {}", chargeId, e.getReason());
        }
    }

    // ─── Webhook do Mercado Pago ─────────────────────────────────

    @Transactional
    public void handleWebhook(String paymentId) {
        // Webhook só faz sentido com Mercado Pago de verdade.
        if (paymentId == null || paymentId.isBlank() || !isLive()) return;

        MercadoPagoClient.Payment payment = mercadoPago.getPayment(paymentId);
        CommissionCharge charge = chargeRepository.findByMpPaymentId(paymentId).orElse(null);
        if (charge == null && payment.externalReference() != null
                && payment.externalReference().startsWith("commission-charge-")) {
            Long id = parseChargeId(payment.externalReference());
            if (id != null) charge = chargeRepository.findById(id).orElse(null);
        }
        if (charge == null) {
            log.info("Webhook MP para pagamento {} sem CommissionCharge correspondente.", paymentId);
            return;
        }
        if (charge.getStatus() == CommissionChargeStatus.PAID
                || charge.getStatus() == CommissionChargeStatus.CANCELED) {
            return; // ja finalizada
        }
        reconcile(charge, payment.status(), payment.statusDetail(), payment.id());
    }

    // ─── Retry pelo contratante ──────────────────────────────────

    @Transactional
    public BillingStatusDTO retryBlockingCharge(Company company) {
        requireEnabled();
        retryBlockingChargeInternal(company);
        return getStatus(company);
    }

    private void retryBlockingChargeInternal(Company company) {
        CommissionCharge charge = chargeRepository
                .findFirstByCompanyIdAndStatusInOrderByCreatedAtDesc(company.getId(), UNPAID)
                .orElse(null);
        if (charge == null) {
            // Nada em aberto -- so garante que nao ficou bloqueado a toa.
            profileRepository.findByCompanyId(company.getId()).ifPresent(p -> {
                unblock(p);
                profileRepository.save(p);
            });
            return;
        }
        charge.setStatus(CommissionChargeStatus.PENDING);
        charge.setFailureReason(null);
        charge.setUpdatedAt(LocalDateTime.now());
        chargeRepository.saveAndFlush(charge);
        processCharge(charge.getId());
    }

    // ─── Simulação (modo simulate, sem Mercado Pago) ─────────────

    // Usado pelo Admin em /admin/commission-charges para decidir o resultado de
    // uma cobrança sem Mercado Pago. Só disponível quando isSimulated().
    @Transactional
    public com.main.nexus.dto.CommissionChargeDTO simulateOutcome(Long chargeId, String outcome) {
        if (!isSimulated()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A simulação de cobrança só está disponível no modo simulate.");
        }
        CommissionCharge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found."));
        if (charge.getStatus() != CommissionChargeStatus.PROCESSING
                && charge.getStatus() != CommissionChargeStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta cobrança já foi finalizada.");
        }
        boolean approved = "approved".equalsIgnoreCase(outcome);
        String paymentId = charge.getMpPaymentId() != null ? charge.getMpPaymentId() : "SIM-" + chargeId;
        reconcile(charge, approved ? "approved" : "rejected", "simulado", paymentId);
        return com.main.nexus.dto.CommissionChargeDTO.from(charge);
    }

    // ─── Job de varredura (NexusScheduler) ───────────────────────

    @Transactional
    public void processStuckCharges() {
        // Só varre no modo live -- no simulado o Admin controla cada cobrança.
        if (!isLive()) return;

        // PENDING com cartao no arquivo -- tenta cobrar.
        for (CommissionCharge c : chargeRepository
                .findByStatusOrderByCreatedAtAsc(CommissionChargeStatus.PENDING)) {
            profileRepository.findByCompanyId(c.getCompany().getId())
                    .filter(CompanyBillingProfile::hasCard)
                    .ifPresent(p -> processCharge(c.getId()));
        }
        // PROCESSING ha mais de 15 min -- re-consulta o MP.
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        for (CommissionCharge c : chargeRepository.findStaleProcessing(threshold)) {
            try {
                handleWebhook(c.getMpPaymentId());
            } catch (Exception e) {
                log.warn("Re-consulta da cobranca {} falhou: {}", c.getId(), e.getMessage());
            }
        }
    }

    // ─── Listagens ───────────────────────────────────────────────

    public List<CommissionChargeDTO> chargesFor(Company company) {
        return chargeRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .map(CommissionChargeDTO::from).toList();
    }

    public List<CommissionChargeDTO> listForAdmin(String status) {
        List<CommissionCharge> rows;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            rows = chargeRepository.findByStatusInOrderByCreatedAtDesc(
                    List.of(CommissionChargeStatus.values()));
        } else {
            rows = chargeRepository.findByStatusInOrderByCreatedAtDesc(
                    List.of(parseStatus(status)));
        }
        return rows.stream().map(CommissionChargeDTO::from).toList();
    }

    // Usado pelo Admin (Prompt 3) para exibir o estado da cobranca junto da confirmacao.
    public CommissionCharge findChargeByConfirmation(Long matchConfirmationId) {
        return chargeRepository.findByMatchConfirmationId(matchConfirmationId).orElse(null);
    }

    // ─── Internos ────────────────────────────────────────────────

    private void reconcile(CommissionCharge charge, String status, String statusDetail, String paymentId) {
        charge.setMpPaymentId(paymentId);
        charge.setMpStatusDetail(statusDetail);
        charge.setUpdatedAt(LocalDateTime.now());
        CompanyBillingProfile p = profileFor(charge.getCompany());

        if ("approved".equals(status)) {
            charge.setStatus(CommissionChargeStatus.PAID);
            charge.setPaidAt(LocalDateTime.now());
            charge.setFailureReason(null);
            chargeRepository.save(charge);
            // Desbloqueia se nao houver mais nada em aberto.
            if (!chargeRepository.existsByCompanyIdAndStatusIn(charge.getCompany().getId(), UNPAID)) {
                unblock(p);
                profileRepository.save(p);
            }
            User u = charge.getCompany().getUser();
            if (u != null) {
                notificationService.notifyCommissionPaid(
                        u, charge.getAmount(),
                        charge.getMatchConfirmation().getMatch().getProject().getTitle());
            }
            // Emissão automática da NFS-e (Prompt 6) -- após o commit desta cobrança.
            eventPublisher.publishEvent(new CommissionChargePaidEvent(charge.getId()));
        } else if ("rejected".equals(status) || "cancelled".equals(status)) {
            charge.setStatus(CommissionChargeStatus.FAILED);
            charge.setFailureReason("Cartão recusado pelo Mercado Pago"
                    + (statusDetail != null ? " (" + statusDetail + ")" : "") + ".");
            chargeRepository.save(charge);
            block(p, PaymentBlockReason.CHARGE_DECLINED, null);
            profileRepository.save(p);
        } else {
            // in_process / pending / authorized -- aguarda webhook / varredura.
            charge.setStatus(CommissionChargeStatus.PROCESSING);
            chargeRepository.save(charge);
        }
    }

    private BillingStatusDTO toStatusDTO(CompanyBillingProfile p, CommissionCharge pending) {
        boolean blocked = Boolean.TRUE.equals(p.getPaymentBlocked());
        return new BillingStatusDTO(
                isBillingEnabled(),
                p.hasCard(),
                p.getCardBrand(),
                p.getCardLast4(),
                p.getCardExpMonth(),
                p.getCardExpYear(),
                p.getCardholderName(),
                blocked,
                p.getBlockReason() != null ? p.getBlockReason().name() : null,
                blocked ? blockMessage(p.getBlockReason()) : null,
                pending != null ? pending.getId() : null,
                pending != null ? pending.getAmount() : null,
                pending != null ? pending.getStatus().name() : null);
    }

    private void requireEnabled() {
        if (!isBillingEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A cobrança de comissão não está habilitada nesta instância.");
        }
    }

    private Long parseChargeId(String externalReference) {
        try {
            return Long.parseLong(externalReference.substring("commission-charge-".length()));
        } catch (Exception e) {
            return null;
        }
    }

    private CommissionChargeStatus parseStatus(String s) {
        try {
            return CommissionChargeStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown charge status: " + s);
        }
    }
}
