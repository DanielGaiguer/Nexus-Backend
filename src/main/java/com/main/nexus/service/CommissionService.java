package com.main.nexus.service;

import com.main.nexus.dto.CommissionPolicyDTO;
import com.main.nexus.dto.ContractorCommissionStatusDTO;
import com.main.nexus.model.CommissionCharge;
import com.main.nexus.model.CommissionPolicy;
import com.main.nexus.model.Company;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.repository.CommissionPolicyRepository;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Camada financeira -- Prompt 1: configuracao do percentual de comissao
// (CommissionPolicy, singleton) e leitura de quantas contratacoes gratuitas
// restam para cada contratante. As 3 primeiras contratacoes fechadas com
// sucesso de cada contratante sao isentas; a partir da 4a a comissao se aplica.
//
// Nada e cobrado nesta etapa. O valor da comissao depende da janela de
// confirmacao (Prompt 2) e a cobranca vem no Prompt 5.
@Service
public class CommissionService {

    // As 3 primeiras contratacoes fechadas com sucesso de cada contratante nao
    // pagam comissao (periodo de teste gratuito da camada financeira).
    public static final int FREE_HIRES_LIMIT = 3;

    private static final BigDecimal DEFAULT_PERCENTAGE = new BigDecimal("10.00");
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    @Autowired
    private CommissionPolicyRepository commissionPolicyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ─── Politica de comissao (Admin) ────────────────────────────────

    // Linha unica -- criada com o padrao (10%) na primeira leitura, para o
    // Admin nunca abrir a tela e nao encontrar registro.
    @Transactional
    public CommissionPolicy getPolicy() {
        return commissionPolicyRepository.findById(CommissionPolicy.SINGLETON_ID)
                .orElseGet(() -> {
                    CommissionPolicy fresh = new CommissionPolicy();
                    fresh.setId(CommissionPolicy.SINGLETON_ID);
                    fresh.setPercentage(DEFAULT_PERCENTAGE);
                    fresh.setUpdatedAt(LocalDateTime.now());
                    return commissionPolicyRepository.save(fresh);
                });
    }

    @Transactional
    public CommissionPolicyDTO getPolicyDTO() {
        return CommissionPolicyDTO.from(getPolicy());
    }

    @Transactional
    public CommissionPolicyDTO updatePolicy(BigDecimal percentage, Long adminUserId) {
        if (percentage == null
                || percentage.signum() < 0
                || percentage.compareTo(MAX_PERCENTAGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The commission percentage must be between 0 and 100.");
        }

        CommissionPolicy policy = getPolicy();
        policy.setPercentage(percentage.setScale(2, RoundingMode.HALF_UP));
        policy.setUpdatedAt(LocalDateTime.now());
        if (adminUserId != null) {
            userRepository.findById(adminUserId).ifPresent(policy::setUpdatedByAdmin);
        }
        return CommissionPolicyDTO.from(commissionPolicyRepository.save(policy));
    }

    // ─── Situacao do contratante ─────────────────────────────────────

    @Transactional
    public ContractorCommissionStatusDTO getContractorStatus(Company company) {
        int used = company.getSuccessfulHiresCount() == null
                ? 0 : company.getSuccessfulHiresCount();
        int remaining = Math.max(0, FREE_HIRES_LIMIT - used);
        boolean commissionApplies = used >= FREE_HIRES_LIMIT;

        return new ContractorCommissionStatusDTO(
                FREE_HIRES_LIMIT,
                used,
                remaining,
                commissionApplies,
                getPolicy().getPercentage());
    }

    // ─── Contratacao confirmada ─────────────────────────────────────

    // Chamado quando uma MatchConfirmation vira CONFIRMED (Prompt 2 automatico ou
    // Prompt 3 pelo Admin). Incrementa o contador de contratacoes fechadas com
    // sucesso do contratante e, se essa contratacao ja esta fora das 3 gratuitas
    // e o billing esta ligado, cria a cobranca da comissao (Prompt 5) e publica
    // um evento -- a cobranca no Mercado Pago acontece apos o commit (BillingEventListener).
    @Transactional
    public void onHireConfirmed(MatchConfirmation confirmation) {
        Company company = confirmation.getMatch().getProject().getCompany();
        int before = company.getSuccessfulHiresCount() == null ? 0 : company.getSuccessfulHiresCount();
        int after = before + 1;
        company.setSuccessfulHiresCount(after);
        companyRepository.save(company);

        boolean billable = after > FREE_HIRES_LIMIT;
        if (!billable || !billingService.isBillingEnabled()) {
            return;
        }

        BigDecimal base = confirmation.getConfirmedAmount();
        if (base == null || base.signum() <= 0) {
            log.warn("Confirmacao {} sem valor confirmado -- nao gera cobranca.", confirmation.getId());
            return;
        }
        BigDecimal pct = getPolicy().getPercentage();
        BigDecimal amount = base.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            return; // comissao de 0% -> nao cobra
        }

        CommissionCharge charge = billingService.createCharge(confirmation, base, pct, amount);
        eventPublisher.publishEvent(new CommissionChargeCreatedEvent(charge.getId()));
    }
}
