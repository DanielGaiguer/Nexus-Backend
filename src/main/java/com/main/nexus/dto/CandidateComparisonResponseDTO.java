
package com.main.nexus.dto;

import java.util.List;

public record CandidateComparisonResponseDTO(
        Long projectId,
        String projectTitle,
        List<String> requiredSkills,
        String workMode,
        String experienceLevelRequired,
        Double minimumBudget,
        Double maximumBudget,
        List<CandidateComparisonItemDTO> candidates
) {}