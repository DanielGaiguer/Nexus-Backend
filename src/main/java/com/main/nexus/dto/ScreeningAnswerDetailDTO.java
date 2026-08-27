package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningQuestionType;
import java.util.List;

// Detalhe de uma resposta pós-submissão -- usado tanto na decisão da empresa (qual alternativa
// foi marcada, qual é a correta, acerto/erro, tempo, texto dissertativo) quanto na tela do
// profissional vendo o próprio resultado. Igual pros dois lados -- a única informação restrita
// (tabSwitchCount) fica no nível de ScreeningInvitationDetailDTO, não aqui. Sem nota por questão
// -- a empresa decide de forma binária pela etapa inteira (ver
// ScreeningInvitationDetailDTO.companyDecisionComment).
public record ScreeningAnswerDetailDTO(
        Long answerId,
        Long questionId,
        ScreeningQuestionType type,
        String prompt,
        List<String> options,

        // MULTIPLE_CHOICE
        Integer selectedOptionIndex,
        Integer correctOptionIndex,
        Boolean correct,

        // ESSAY
        String essayText,

        Integer timeSpentSeconds
) {}
