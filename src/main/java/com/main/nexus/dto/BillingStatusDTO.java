package com.main.nexus.dto;

// GET /api/company/billing/status -- situacao de pagamento do contratante logado.
public record BillingStatusDTO(
        boolean billingEnabled,
        boolean hasCard,
        String cardBrand,
        String cardLast4,
        Integer cardExpMonth,
        Integer cardExpYear,
        String cardholderName,
        boolean blocked,
        String blockReason,     // NO_CARD_ON_FILE | CHARGE_DECLINED | null
        String blockMessage,    // texto pronto para exibir
        // Resumo da cobranca pendente/recusada que precisa de acao, se houver.
        Long pendingChargeId,
        java.math.BigDecimal pendingChargeAmount,
        String pendingChargeStatus
) {}
