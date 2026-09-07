package com.main.nexus.dto;

import java.time.LocalDateTime;

// Um convite PENDING de uma conta empresarial. `id` é usado por
// DELETE /api/company/invitations/{id} (revogar).
public record CompanyInvitationDTO(
        Long id,
        String email,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {}
