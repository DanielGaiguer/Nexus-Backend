package com.main.nexus.dto;

// Corpo de PUT /api/admin/fiscal-config.
public record UpdateFiscalConfigDTO(
        String enotasEmpresaId,
        String defaultServiceDescription
) {}
