package com.main.nexus.dto;

import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectType;
import java.util.List;

public record ProfessionalProfileDTO(
        Long id,
        String name,
        String email,
        String phone,
        String city,
        String state,
        String cep,
        Boolean available,
        Double reputation,
        Double latitude,
        Double longitude,
        List<String> skills,
        List<ProjectType> preferredTypes,
        ExperienceLevel experienceLevel,
        String profilePhotoUrl,

        // Novos campos de regime
        List<OpportunityType> preferredOpportunityTypes,
        Double expectedSalaryCLT,
        Double expectedSalaryPJ,
        Double freelanceMinExpectation,
        Double freelanceMaxExpectation,

        // Status de completude
        boolean profileComplete,
        List<String> missingFields,

        String linkedinUrl,
        String resume
) {}