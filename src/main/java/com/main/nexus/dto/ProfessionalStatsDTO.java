package com.main.nexus.dto;

public record ProfessionalStatsDTO(
        int totalMatchesReceived,
        int totalAccepted,
        int totalRejectedByMe,
        double acceptanceRate,
        double avgScore,
        String rankingPosition
) {}
