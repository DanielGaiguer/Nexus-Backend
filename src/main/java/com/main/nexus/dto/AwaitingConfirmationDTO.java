package com.main.nexus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Uma contratacao fechada cuja janela de confirmacao de 30 dias ainda esta
// aberta -- ainda nao gerou cobranca. Aparece no extrato do contratante como
// "aguardando confirmacao" (Prompt 7). `estimatedCommission` = valor sugerido x
// percentual atual da politica (so estimativa).
public record AwaitingConfirmationDTO(
        Long matchId,
        String projectTitle,
        String professionalName,
        LocalDateTime openedAt,
        LocalDateTime deadline,
        BigDecimal suggestedAmount,
        BigDecimal estimatedCommission
) {}
