package com.main.nexus.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MapOpportunityDTO(
        Long id,
        String opportunityType,
        String title,
        String companyName,
        String city,
        String uf,
        Double latitude,
        Double longitude,
        String workMode,
        List<String> requiredSkills,

        String experienceLevel,
        String projectType,
        String contractType,
        Double monthlySalaryMin,
        Double monthlySalaryMax,
        Double minimumBudget,
        Double maximumBudget,
        LocalDateTime createdAt
) {}
