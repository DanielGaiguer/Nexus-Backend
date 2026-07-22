// CandidateComparisonItemDTO.java
package com.main.nexus.dto;

import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.StatusMatch;
import java.util.List;

public record CandidateComparisonItemDTO(
        Long matchId,
        Long professionalId,
        String name,
        String city,
        String state,
        String profilePhotoUrl,
        ExperienceLevel experienceLevel,
        Double reputation,
        Double confidenceScore,
        Integer totalReviews,
        Double minimumSalary,
        Double maximumSalary,
        List<String> skills,
        List<String> matchingSkills,
        List<String> missingSkills,
        Integer previousProjectsCount,
        Boolean available,
        StatusMatch matchStatus,
        ScoreBreakdownDTO scoreBreakdown
) {}