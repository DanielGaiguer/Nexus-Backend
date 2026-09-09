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
        // null numa etapa BEHAVIORAL -- la nao ha resposta certa pra contar.
        Double autoScorePercent,
        // Perfil de tracos, null fora de etapa BEHAVIORAL. Chega ate o card do Kanban por este
        // campo (PipelineCardDTO.latestScreening) -- INFORMATIVO, como o resto do badge: nada no
        // pipeline le estes numeros como criterio de avanco.
        ScreeningTraitProfileDTO traitProfile
) {}
