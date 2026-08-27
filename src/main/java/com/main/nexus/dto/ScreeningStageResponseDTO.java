package com.main.nexus.dto;

import java.util.List;

public record ScreeningStageResponseDTO(
        Long id,
        Integer orderIndex,
        String title,
        String instructions,
        Integer responseDeadlineDays,
        Boolean active,
        List<ScreeningQuestionResponseDTO> questions
) {}
