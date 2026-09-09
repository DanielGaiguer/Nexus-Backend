package com.main.nexus.dto;

import com.main.nexus.model.enums.PendingIntentType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.ScreeningStageKind;
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
        // Instruções GERAIS do processo (ScreeningQuestionnaire.instructions) -- diferente de
        // `instructions` abaixo, que é só desta etapa. Antes não chegava pra nenhum dos dois lados.
        String questionnaireInstructions,
        Long screeningStageId,
        String stageTitle,
        // Diz à tela de revisão o que ela está olhando. Sem isto o front teria que INFERIR o tipo
        // da etapa ("tem traitProfile, logo é comportamental"), o que falha justamente no caso
        // que mais importa: uma etapa comportamental ainda não respondida não tem perfil nenhum,
        // e apareceria como uma etapa de perguntas vazia.
        ScreeningStageKind stageKind,
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
        // null numa etapa BEHAVIORAL: ali não existe resposta certa, e o resultado é o
        // traitProfile abaixo, não uma nota.
        Double autoScorePercent,
        // Perfil de traços do Big Five -- preenchido só em etapa BEHAVIORAL, null nas demais. O
        // aviso obrigatório viaja dentro dele (ver ScreeningTraitProfileDTO).
        ScreeningTraitProfileDTO traitProfile,
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
