package com.main.nexus.dto;

import com.main.nexus.model.enums.ContractType;
import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectResponseDTO(
        Long id,
        String title,
        String description,
        Modality workMode,
        ExperienceLevel experienceLevel,
        ProjectStatus status,
        Integer maxPositions,
        Integer filledPositions,
        LocalDateTime createdAt,
        OpportunityType opportunityType,
        List<String> requiredSkills,
        Long companyId,
        String companyName,

        // PROJECT
        Double minimumBudget,
        Double maximumBudget,
        LocalDate deadline,

        // JOB
        Double monthlySalaryMin,
        Double monthlySalaryMax,
        ContractType contractType,
        String benefits,
        LocalDate startDate,
        Integer workloadHoursPerWeek
) {}