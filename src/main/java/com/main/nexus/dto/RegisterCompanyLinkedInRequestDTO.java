package com.main.nexus.dto;

import com.main.nexus.model.enums.CompanyType;

public record RegisterCompanyLinkedInRequestDTO(
        String ticket,
        String companyName,
        String taxId,
        String phone,
        String cep,
        String description,
        CompanyType type
) {}
