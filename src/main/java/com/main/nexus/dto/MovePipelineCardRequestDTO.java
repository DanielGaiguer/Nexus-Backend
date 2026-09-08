package com.main.nexus.dto;

// Corpo do PUT /api/projects/{projectId}/pipeline/matches/{matchId}/stage -- só a coluna de
// destino. Este endpoint nunca altera StatusMatch/RejectionFeedback/MatchConfirmation.
public record MovePipelineCardRequestDTO(
        Long stageId
) {}
