package com.main.nexus.dto;

// Corpo de PUT /api/company/billing/fiscal-profile.
public record UpdateCompanyFiscalProfileDTO(
        String legalName,
        String fiscalEmail,
        String street,
        String number,
        String complement,
        String district,
        String cityIbgeCode
) {}
