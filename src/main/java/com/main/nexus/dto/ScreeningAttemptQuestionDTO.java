package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningQuestionType;
import java.util.List;

// Versão da questão exibida ao profissional ENQUANTO ele responde -- sem correctOptionIndex,
// que só é revelado depois da submissão (ver ScreeningAnswerDetailDTO).
public record ScreeningAttemptQuestionDTO(
        Long id,
        ScreeningQuestionType type,
        String prompt,
        List<String> options
) {}
