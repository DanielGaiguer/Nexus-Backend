package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningQuestionType;
import java.util.List;

// Visão completa da questão, com gabarito -- só devolvida pra empresa dona do
// ScreeningQuestionnaire (criação/edição/avaliação). Ver ScreeningAttemptQuestionDTO para a
// versão sem gabarito exibida ao profissional antes da submissão.
public record ScreeningQuestionResponseDTO(
        Long id,
        ScreeningQuestionType type,
        String prompt,
        List<String> options,
        Integer correctOptionIndex
) {}
