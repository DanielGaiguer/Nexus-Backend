
package com.main.nexus.dto;

public record CompanyProfileDTO(
        Long id,
        String companyName,
        String email,
        String taxId,
        String phone,
        String city,
        String uf,
        String cep,
        String description,
        Double reputation,
        Double latitude,
        Double longitude,
        String status,
        String profilePhotoUrl,
        String linkedinUrl,
        String type
) {}