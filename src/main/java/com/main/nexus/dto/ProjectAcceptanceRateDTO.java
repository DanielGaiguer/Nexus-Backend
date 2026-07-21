package com.main.nexus.dto;

public record ProjectAcceptanceRateDTO(
        Long projectId,
        String projectTitle,
        long totalMatches,
        long confirmedMatches,
        long rejectedMatches,
        double acceptanceRate,
        double averageScore
) {}