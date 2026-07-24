package com.main.nexus.dto;

public record PublicCompanyDTO(
        Long id,
        String companyName,
        String description,
        String city,
        String uf,
        Double reputation,
        String profilePhotoUrl
) {}
