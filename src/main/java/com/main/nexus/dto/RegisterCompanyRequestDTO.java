package com.main.nexus.dto;

import com.main.nexus.model.enums.CompanyType;

public record RegisterCompanyRequestDTO(
        String email,
        String password,
        String companyName,
        String taxId,
        String phone,
        String cep,
        String description,
        CompanyType type,
        // Consentimento LGPD. acceptedTermsOfUse obrigatório (true); os outros
        // dois são opcionais e não bloqueiam o cadastro.
        Boolean acceptedTermsOfUse,
        Boolean acceptedMarketingCommunications,
        Boolean acceptedAlgorithmImprovement
) {}
