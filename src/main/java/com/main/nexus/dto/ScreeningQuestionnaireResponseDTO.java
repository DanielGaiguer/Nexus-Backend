package com.main.nexus.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ScreeningQuestionnaireResponseDTO(
        Long id,
        Long projectId,
        String projectTitle,
        String title,
        String instructions,
        LocalDateTime createdAt,
        List<ScreeningStageResponseDTO> stages
) {}
