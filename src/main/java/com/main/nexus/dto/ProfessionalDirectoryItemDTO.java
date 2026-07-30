package com.main.nexus.dto;

import java.util.List;

public record ProfessionalDirectoryItemDTO(
        Long id,
        String name,
        String city,
        String uf,
        Double reputation,
        String profilePhotoUrl,
        String experienceLevel,
        List<String> skills
) {}
