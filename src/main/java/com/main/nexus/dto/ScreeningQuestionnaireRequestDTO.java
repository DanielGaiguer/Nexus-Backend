package com.main.nexus.dto;

import java.util.List;

public record ScreeningQuestionnaireRequestDTO(
        Long projectId,
        String title,
        String instructions,
        List<ScreeningStageRequestDTO> stages
) {}
