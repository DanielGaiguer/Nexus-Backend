package com.main.nexus.dto;

import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

// Corpo de PUT /api/admin/custom-portals/{id}/subscription — o Admin ajusta a
// mao plano, valor, proximo vencimento e situacao de pagamento.
public record UpdateCustomPortalSubscriptionDTO(
        String planName,
        BigDecimal planPrice,
        LocalDate nextDueDate,
        CustomPortalPaymentStatus paymentStatus
) {}
