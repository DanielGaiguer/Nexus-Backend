
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
        CompanyType type
) {}