package com.main.nexus.dto;

public record MapCompanyDTO(
        Long id,
        String companyName,
        String city,
        String uf,
        Double latitude,
        Double longitude,
        Double reputation,
        Integer openProjects,
        String type
) {}