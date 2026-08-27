package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningInvitationStatus;
import java.time.LocalDateTime;

// Versão compacta, sem gabarito nem analytics sensíveis (tabSwitchCount) -- segura pra embutir
// em qualquer lista/comparação (MatchResponseDTO, CandidateComparisonItemDTO,
// ProposalResponseDTO) sem vazar dado que só deveria aparecer na tela de detalhe/decisão.
// stageOrderIndex/totalStages dão o contexto de progresso ("Etapa 2 de 3").
public record ScreeningInvitationSummaryDTO(
        Long id,
        Long screeningQuestionnaireId,
        String screeningQuestionnaireTitle,
        String stageTitle,
        Integer stageOrderIndex,
        Integer totalStages,
        ScreeningInvitationStatus status,
        LocalDateTime sentAt,
        LocalDateTime deadlineAt,
        LocalDateTime submittedAt,
        Double autoScorePercent
) {}
