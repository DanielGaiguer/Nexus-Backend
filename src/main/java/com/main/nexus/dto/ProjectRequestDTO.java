package com.main.nexus.dto;

import com.main.nexus.model.enums.ContractType;
import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectType;
import java.time.LocalDate;
import java.util.List;

public record ProjectRequestDTO(
        // Campos comuns
        String title,
        String description,
        Modality workMode,
        ProjectType type,
        ExperienceLevel experienceLevel,
        Integer maxPositions,
        OpportunityType opportunityType,
        List<Long> skillIds,
        String cep,

        // Campos exclusivos de PROJECT
        Double minimumBudget,
        Double maximumBudget,
        LocalDate deadline,

        // Campos exclusivos de JOB
        Double monthlySalaryMin,
        Double monthlySalaryMax,
        ContractType contractType,
        String benefits,
        LocalDate startDate,
        Integer workloadHoursPerWeek,

        // Visibilidade — nulo é tratado como "visível/aberto" (comportamento anterior à feature)
        Boolean visibleToCompanies,
        Boolean salaryVisibleToProfessionals,
        Boolean salaryVisibleToCompanies
) {}