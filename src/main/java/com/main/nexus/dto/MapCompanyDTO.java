package com.main.nexus.dto;

public record MapCompanyDTO(
        Long id,
        String companyName,
        String city,
        String state,
        Double latitude,
        Double longitude,
        Double reputation,
        Integer openProjects
) {}