package com.main.nexus.dto;

import com.main.nexus.model.enums.OpportunityType;

public record ProjectAcceptanceRateDTO(
        Long projectId,
        String projectTitle,
        OpportunityType opportunityType,
        long totalMatches,
        long confirmedMatches,
        long rejectedMatches,
        double acceptanceRate
) {}