package com.main.nexus.dto;

// Dados fiscais do contratante (tomador). GET/PUT em /api/company/billing/fiscal-profile.
// taxId / companyName / city / uf / cep vem do Company (somente leitura aqui).
public record CompanyFiscalProfileDTO(
        String taxId,
        String companyType,     // INDIVIDUAL | LEGAL_ENTITY
        String companyName,
        String city,
        String uf,
        String cep,
        String legalName,
        String fiscalEmail,
        String street,
        String number,
        String complement,
        String district,
        String cityIbgeCode,
        boolean complete
) {}
