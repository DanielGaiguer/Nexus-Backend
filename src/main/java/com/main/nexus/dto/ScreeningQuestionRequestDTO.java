package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningQuestionType;
import java.util.List;

// `id` nulo = questão nova; preenchido = edita a existente no lugar (preserva a identidade da
// linha, já que respostas antigas referenciam a questão por id -- ver
// ScreeningQuestionnaireService.mergeQuestions).
public record ScreeningQuestionRequestDTO(
        Long id,
        ScreeningQuestionType type,
        String prompt,
        List<String> options,
        Integer correctOptionIndex
) {}
