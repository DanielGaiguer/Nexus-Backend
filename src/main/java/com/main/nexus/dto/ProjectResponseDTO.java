package com.main.nexus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.main.nexus.model.enums.ContractType;
import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.ProjectType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectResponseDTO(
        Long id,
        String title,
        String description,
        @JsonProperty("modality") Modality workMode,
        ExperienceLevel experienceLevel,
        ProjectStatus status,
        Integer maxPositions,
        Integer filledPositions,
        LocalDateTime createdAt,
        OpportunityType opportunityType,
        ProjectType type,
        List<SkillResponseDTO> requiredSkills,
        Long companyId,
        String companyName,
        
        String cep,
        Double latitude,
        Double longitude,
        String city,
        String uf,

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
        Integer workloadHoursPerWeek,

        // Visibilidade. Os dois campos de configuração só vêm preenchidos para o dono/ADMIN;
        // para os demais viewers, vêm nulos e "salaryVisible" já diz se o salário pode ser exibido.
        Boolean visibleToCompanies,
        Boolean salaryVisibleToProfessionals,
        Boolean salaryVisibleToCompanies,
        Boolean salaryVisible,

        Boolean acceptsProposals,

        PublicCompanyDTO company
) {}