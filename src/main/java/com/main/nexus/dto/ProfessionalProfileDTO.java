
package com.main.nexus.dto;

import com.main.nexus.model.enums.ProjectType;
import com.main.nexus.model.enums.ExperienceLevel;
import java.util.List;

public record ProfessionalProfileDTO(
        Long id,
        String name,
        String email,
        String phone,
        String city, 
        String uf,
        String cep,
        Double minimumSalary,
        Double maximumSalary,
        Boolean available,
        Double reputation,
        Double latitude,
        Double longitude,
        List<String> skills,
        List<ProjectType> preferredTypes,
        ExperienceLevel experienceLevel
) {}