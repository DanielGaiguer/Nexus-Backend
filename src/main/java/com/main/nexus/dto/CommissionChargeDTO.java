package com.main.nexus.dto;

import com.main.nexus.model.CommissionCharge;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Uma cobranca de comissao para as telas (historico do contratante + fila do Admin).
public record CommissionChargeDTO(
        Long id,
        Long matchId,
        Long companyId,
        String companyName,
        String projectTitle,
        String professionalName,
        BigDecimal baseAmount,
        BigDecimal percentage,
        BigDecimal amount,
        String status,
        String mpPaymentId,
        String mpStatusDetail,
        String failureReason,
        int attempts,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
    public static CommissionChargeDTO from(CommissionCharge c) {
        var match = c.getMatchConfirmation().getMatch();
        return new CommissionChargeDTO(
                c.getId(),
                match.getId(),
                c.getCompany().getId(),
                c.getCompany().getCompanyName(),
                match.getProject().getTitle(),
                match.getProfessional().getName(),
                c.getBaseAmount(),
                c.getPercentage(),
                c.getAmount(),
                c.getStatus().name(),
                c.getMpPaymentId(),
                c.getMpStatusDetail(),
                c.getFailureReason(),
                c.getAttempts(),
                c.getCreatedAt(),
                c.getPaidAt());
    }
}
