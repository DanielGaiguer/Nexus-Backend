package com.main.nexus.dto;

public record CompanyDirectoryItemDTO(
        Long id,
        String companyName,
        String city,
        String uf,
        Double reputation,
        String profilePhotoUrl,
        String description,
        String type
) {}
