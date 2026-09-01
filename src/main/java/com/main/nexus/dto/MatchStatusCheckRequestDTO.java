package com.main.nexus.dto;

import com.main.nexus.model.enums.MatchOutcome;
import java.math.BigDecimal;

// Corpo de POST /api/matches/{id}/status-check. `finalAmount` e obrigatorio
// quando o outcome indica que houve trabalho (WORKING_TOGETHER / PROJECT_COMPLETED)
// e ignorado caso contrario -- ver MatchStatusCheckService.recordAnswer.
public record MatchStatusCheckRequestDTO(
        MatchOutcome outcome,
        BigDecimal finalAmount
) {}
