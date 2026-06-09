
package com.main.nexus.dto;

import java.util.List;

public record ProfessionalProfileDTO(
        Long id,
        String name,
        String email,
        String phone,
        String city,
        Double minimumSalary,
        Double maximumSalary,
        Boolean available,
        Double reputation,
        Double latitude,
        Double longitude,
        List<String> skills
) {}