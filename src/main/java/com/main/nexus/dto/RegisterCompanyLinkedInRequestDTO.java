package com.main.nexus.dto;

public record RegisterCompanyLinkedInRequestDTO(
        String ticket,
        String companyName,
        String taxId,
        String phone,
        String cep,
        String description
) {}
