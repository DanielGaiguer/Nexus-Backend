package com.main.nexus.dto;

import java.util.List;

// Resposta de GET /api/company/members: membros ACTIVE + convites PENDING.
public record CompanyMembersResponseDTO(
        List<CompanyMemberDTO> members,
        List<CompanyInvitationDTO> pendingInvitations
) {}
