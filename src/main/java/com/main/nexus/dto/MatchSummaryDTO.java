package com.main.nexus.dto;

public record MatchSummaryDTO(
        long totalMatches,
        long confirmedMatches,
        long pendingMatches,
        long rejectedMatches,
        double overallAcceptanceRate
) {}