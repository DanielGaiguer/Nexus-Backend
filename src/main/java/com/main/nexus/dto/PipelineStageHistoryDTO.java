package com.main.nexus.dto;

import java.time.LocalDateTime;

// Uma linha da trilha de movimentação de um card. COMPANY-only -- nunca exposta no
// MatchController (que os dois lados usam). `movedByEmail` identifica o membro que arrastou
// (membros da conta são identificados por e-mail, ver CompanyMemberDTO).
public record PipelineStageHistoryDTO(
        Long id,
        Long matchId,
        Long fromStageId,
        String fromStageName,
        Long toStageId,
        String toStageName,
        Long movedByUserId,
        String movedByEmail,
        LocalDateTime movedAt
) {}
