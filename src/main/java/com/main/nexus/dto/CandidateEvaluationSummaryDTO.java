package com.main.nexus.dto;

import java.util.List;

// Consolidado do scorecard de um candidato: média ao vivo (nunca persistida, mesmo padrão de
// MatchService.getScoreBreakdown) + contagem + a lista de pareceres individuais. `average` é null
// quando ainda não há nenhum parecer.
public record CandidateEvaluationSummaryDTO(
        Double average,
        int count,
        List<CandidateEvaluationItemDTO> items
) {}
