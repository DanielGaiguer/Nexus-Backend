package com.main.nexus.dto;

public record CompanyAcceptanceRateDTO(
        Long companyId,
        String companyName,
        long totalMatches,
        long confirmedMatches,
        long rejectedMatches,
        double acceptanceRate,
        double averageScore
) {}
