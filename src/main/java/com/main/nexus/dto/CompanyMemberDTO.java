package com.main.nexus.dto;

import java.time.LocalDateTime;

// Uma linha de membro ACTIVE numa conta empresarial. `id` é o id do CompanyMember
// (usado por DELETE /api/company/members/{id} e /transfer-ownership/{id}).
public record CompanyMemberDTO(
        Long id,
        Long userId,
        String email,
        String role,
        LocalDateTime joinedAt
) {}
