package com.main.nexus.dto;

import com.main.nexus.model.CustomPortalRequest;
import java.time.LocalDateTime;

// Representacao de uma solicitacao de plataforma personalizada para as telas
// (contratante e Admin). status vai como String crua do enum, mesmo padrao de
// CompanyProfileDTO.status.
public record CustomPortalRequestDTO(
        Long id,
        Long companyId,
        String companyName,
        String companyEmail,
        LocalDateTime requestedAt,
        String message,
        String status,
        String reviewedByEmail,
        LocalDateTime reviewedAt,
        String decisionReason
) {
    public static CustomPortalRequestDTO from(CustomPortalRequest r) {
        return new CustomPortalRequestDTO(
                r.getId(),
                r.getCompany().getId(),
                r.getCompany().getCompanyName(),
                r.getCompany().getUser() != null ? r.getCompany().getUser().getEmail() : null,
                r.getRequestedAt(),
                r.getMessage(),
                r.getStatus().name(),
                r.getReviewedBy() != null ? r.getReviewedBy().getEmail() : null,
                r.getReviewedAt(),
                r.getDecisionReason()
        );
    }
}
