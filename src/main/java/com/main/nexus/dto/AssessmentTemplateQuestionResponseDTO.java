package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningQuestionType;
import java.util.List;

// Inclui o gabarito -- só a empresa dona do molde chega até aqui, e é ela quem o escreveu. O
// candidato nunca vê um molde: ele vê a ScreeningQuestion copiada, por ScreeningAttemptQuestionDTO,
// que omite correctOptionIndex.
public record AssessmentTemplateQuestionResponseDTO(
        Long id,
        ScreeningQuestionType type,
        String prompt,
        List<String> options,
        Integer correctOptionIndex
) {}
