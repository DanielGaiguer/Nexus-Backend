package com.main.nexus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// GET /api/company/custom-portal/subscription -- situação da assinatura da
// plataforma personalizada do contratante logado (para o card "Assinatura &
// cobrança"). `publicKey` alimenta o Card Brick do Mercado Pago no frontend.
public record PortalSubscriptionStatusDTO(
        boolean hasPortal,
        boolean billingEnabled,
        boolean simulated,
        String publicKey,
        String portalStatus,       // ACTIVE | SUSPENDED | CANCELED
        String paymentStatus,      // UP_TO_DATE | OVERDUE | CANCELED
        String planName,
        BigDecimal planPrice,
        LocalDate nextDueDate,
        LocalDate paymentGraceUntil,
        boolean hasCard,
        String cardBrand,
        String cardLast4
) {}
