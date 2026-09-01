package com.main.nexus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Uma janela de confirmação vista pelo Admin (fila / drill-down por empresa).
// Traz o que cada lado respondeu, para a avaliação manual.
public record AdminMatchConfirmationDTO(
        Long matchId,
        Long companyId,
        String companyName,
        Long professionalId,
        String professionalName,
        String projectTitle,
        String opportunityType,
        String status,
        String pendingReason,
        String resolution,          // PARTIES_AGREED | ADMIN_SET_VALUE | ADMIN_COULD_NOT_CONFIRM | null
        LocalDateTime openedAt,
        LocalDateTime deadline,
        LocalDateTime resolvedAt,
        // Dias desde que o caso entrou em PENDING_ADMIN_REVIEW (0 quando não está pendente).
        long daysPending,
        BigDecimal suggestedAmount,
        BigDecimal confirmedAmount,
        String companyOutcome,
        BigDecimal companyAmount,
        String professionalOutcome,
        BigDecimal professionalAmount,
        boolean companyAnswered,
        boolean professionalAnswered,
        boolean adminReviewed,
        String reviewedByAdminEmail,
        LocalDateTime reviewedAt,
        String adminNote,
        // Cobrança de comissão vinculada (Prompt 5). null quando não há (gratuita,
        // sem valor, ou billing desligado).
        String chargeStatus,       // PENDING | PROCESSING | PAID | FAILED | CANCELED | null
        java.math.BigDecimal chargeAmount
) {}
