package com.main.nexus.dto;

import java.time.LocalDateTime;

// Uma nota interna sobre um candidato. COMPANY-only -- nunca serializada num endpoint acessível
// ao profissional. `authorLabel` vem SEMPRE do campo persistido (snapshot), nunca do CompanyMember
// ao vivo. `canEdit` é calculado para o membro que está pedindo (autor original ou OWNER).
public record CompanyCandidateNoteDTO(
        Long id,
        Long professionalId,
        Long matchId,
        Long authorMemberId,
        Long authorUserId,
        String authorLabel,
        boolean canEdit,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
