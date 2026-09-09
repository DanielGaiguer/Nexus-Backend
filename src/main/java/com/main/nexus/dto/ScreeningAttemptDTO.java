package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.ScreeningStageKind;
import java.time.LocalDateTime;
import java.util.List;

// O que o profissional vê ao abrir a tela de resposta (antes de submeter) -- sempre de UMA etapa
// específica; stageOrderIndex/totalStages dão o contexto de progresso ("Etapa 2 de 3").
public record ScreeningAttemptDTO(
        Long invitationId,
        String screeningQuestionnaireTitle,
        // Instruções GERAIS do processo (ScreeningQuestionnaire.instructions) -- diferente de
        // `instructions` abaixo, que é só desta etapa. Antes não chegava pro profissional em
        // nenhuma tela.
        String questionnaireInstructions,
        String stageTitle,
        // Decide a UI inteira da tela de resposta: QUESTIONS renderiza pergunta a pergunta,
        // BEHAVIORAL renderiza a escala Likert fixa de 5 pontos (ver ScreeningStageKind).
        ScreeningStageKind stageKind,
        Integer stageOrderIndex,
        Integer totalStages,
        String instructions,
        ScreeningInvitationStatus status,
        LocalDateTime deadlineAt,
        String projectTitle,
        String companyName,
        List<ScreeningAttemptQuestionDTO> questions,
        // Só relevante numa etapa VIDEO. `videoConsentAccepted` false = a tela tem que colher o
        // aceite antes de deixar gravar (ver ScreeningVideoService).
        boolean videoConsentAccepted,
        String videoConsentText,
        Long videoMaxSizeBytes
) {}
