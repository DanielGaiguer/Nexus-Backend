package com.main.nexus.dto;

import com.main.nexus.model.PortalSubscriptionCharge;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Uma mensalidade da plataforma personalizada para as telas (histórico do
// contratante + fila do Admin).
public record PortalSubscriptionChargeDTO(
        Long id,
        Long portalId,
        Long companyId,
        String companyName,
        String subdomain,
        String planName,
        BigDecimal amount,
        LocalDate dueDate,
        String status,
        String mpPaymentId,
        String failureReason,
        int attempts,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
    public static PortalSubscriptionChargeDTO from(PortalSubscriptionCharge c) {
        var portal = c.getCustomPortal();
        return new PortalSubscriptionChargeDTO(
                c.getId(),
                portal.getId(),
                c.getCompany().getId(),
                c.getCompany().getCompanyName(),
                portal.getSubdomain(),
                portal.getPlanName(),
                c.getAmount(),
                c.getDueDate(),
                c.getStatus().name(),
                c.getMpPaymentId(),
                c.getFailureReason(),
                c.getAttempts(),
                c.getCreatedAt(),
                c.getPaidAt());
    }
}
