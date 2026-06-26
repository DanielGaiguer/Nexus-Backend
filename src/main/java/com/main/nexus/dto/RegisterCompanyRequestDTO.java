
package com.main.nexus.dto;

public record RegisterCompanyRequestDTO(
        String email,
        String password,
        String companyName,
        String taxId,
        String phone,
        String cep,
        String description
) {}