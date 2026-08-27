package com.main.nexus.dto;

import java.util.List;

// `id` nulo = etapa nova; preenchido = edita a existente no lugar (ver
// ScreeningQuestionnaireService.mergeStages).
public record ScreeningStageRequestDTO(
        Long id,
        String title,
        String instructions,
        Integer responseDeadlineDays,
        List<ScreeningQuestionRequestDTO> questions
) {}
