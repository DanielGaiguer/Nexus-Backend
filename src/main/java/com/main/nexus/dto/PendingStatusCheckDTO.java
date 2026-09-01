package com.main.nexus.dto;

import java.math.BigDecimal;

// Janela de confirmacao mais antiga que o usuario logado (contratante OU
// profissional) ainda nao respondeu -- alimenta o dialog "confirmacao pendente"
// no dashboard.
public record PendingStatusCheckDTO(
        Long matchId,
        String otherPartyName,
        String projectTitle,
        String opportunityType,   // "PROJECT" | "JOB" | null
        BigDecimal suggestedAmount
) {}
