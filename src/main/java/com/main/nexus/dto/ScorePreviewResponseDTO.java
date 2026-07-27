
package com.main.nexus.dto;

public record ScorePreviewResponseDTO(
        Double finalScore,
        String matchStatus,
        Long matchId,
        ScoreBreakdownDTO scoreBreakdown
) {}
