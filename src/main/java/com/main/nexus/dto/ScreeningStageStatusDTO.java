package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningInvitationStatus;

// Uma etapa dentro do resumo de um processo seletivo (ScreeningProcessSummaryDTO) -- status e
// invitationId nulos significam que o profissional ainda não chegou nesta etapa (nenhuma
// tentativa criada ainda).
public record ScreeningStageStatusDTO(
        Long stageId,
        Integer orderIndex,
        String title,
        ScreeningInvitationStatus status,
        Long invitationId
) {}
