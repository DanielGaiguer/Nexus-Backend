package com.main.nexus.dto;

import com.main.nexus.model.enums.PendingIntentType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import java.time.LocalDateTime;
import java.util.List;

// Visão completa pós-envio de UMA etapa -- usada tanto pela empresa decidindo (aprovar/reprovar)
// quanto pelo profissional vendo o próprio resultado. Idêntica pros dois lados, exceto
// tabSwitchCount: só populado quando quem monta o DTO é a empresa (ver
// ScreeningInvitationService.toDetailDTO) -- decisão de manter a contagem de saída de aba
// visível só para o contratante.
public record ScreeningInvitationDetailDTO(
        Long id,
        Long screeningQuestionnaireId,
        String screeningQuestionnaireTitle,
        Long screeningStageId,
        String stageTitle,
        Integer stageOrderIndex,
        Integer totalStages,
        String instructions,
        Long projectId,
        String projectTitle,
        Long professionalId,
        String professionalName,

        ScreeningInvitationStatus status,
        LocalDateTime sentAt,
        LocalDateTime deadlineAt,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,

        Integer totalTimeSpentSeconds,
        // Visível só quando o DTO é montado para a empresa -- null para o profissional.
        Integer tabSwitchCount,
        // Referência/sugestão, calculada só das questões MULTIPLE_CHOICE -- nunca decide sozinha.
        Double autoScorePercent,
        String companyDecisionComment,

        // Contexto de qual ação ficou pendente por causa desta etapa -- pendingProposalId só
        // preenchido quando pendingIntentType == PROPOSAL_SUBMIT, usado pela empresa pra exibir
        // a proposta associada num painel separado (ver decisão confirmada com o usuário: aceite/
        // recusa de proposta nunca é automatizado pelo resultado da etapa).
        PendingIntentType pendingIntentType,
        Long pendingProposalId,

        List<ScreeningAnswerDetailDTO> answers,

        // Todas as etapas do questionário (mesmo formato/mesma fonte de ScreeningProcessSummaryDTO
        // -- ver ScreeningInvitationService.buildStageStatusList) -- dá pro front desenhar o fluxo
        // completo de etapas nesta tela, com só o título de cada uma e destaque na etapa atual
        // (screeningStageId acima).
        List<ScreeningStageStatusDTO> stages
) {}
