package com.main.nexus.dto;

import java.util.List;

public record PublicCompanyDTO(
        Long id,
        String companyName,
        String description,
        String city,
        String uf,
        Double reputation,
        String profilePhotoUrl,
        ReputationExplanationDTO reputationDetails,
        String taxId,
        String status,
        List<CompanyPreviousProjectDTO> previousProjects,
        String contactEmail
) {}
