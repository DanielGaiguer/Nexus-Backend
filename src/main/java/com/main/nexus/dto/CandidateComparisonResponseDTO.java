
package com.main.nexus.dto;

import java.util.List;

public record CandidateComparisonResponseDTO(
        Long projectId,
        String projectTitle,
        List<String> requiredSkills,
        String workMode,
        String experienceLevelRequired,
        String opportunityType,
        Double minimumBudget,
        Double maximumBudget,
        Double monthlySalaryMin,
        Double monthlySalaryMax,
        List<CandidateComparisonItemDTO> candidates
) {}