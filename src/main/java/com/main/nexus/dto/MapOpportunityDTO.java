package com.main.nexus.dto;

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
        List<String> requiredSkills
) {}
