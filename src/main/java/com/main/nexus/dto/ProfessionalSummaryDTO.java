package com.main.nexus.dto;

import java.util.List;

public record ProfessionalSummaryDTO(
        Long id,
        String name,
        String phone,
        Double reputation,
        String profilePhotoUrl,
        List<String> skills
) {}
