package com.main.nexus.dto;

// Corpo de POST /api/admin/custom-portal-requests/{id}/reject. reason e
// obrigatorio (validado no service), mesmo padrao de RejectCompanyRequestDTO.
public record RejectCustomPortalRequestDTO(String reason) {}
