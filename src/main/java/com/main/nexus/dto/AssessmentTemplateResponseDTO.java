package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningStageKind;
import java.time.LocalDateTime;
import java.util.List;

public record AssessmentTemplateResponseDTO(
        Long id,
        String title,
        ScreeningStageKind kind,
        String instructions,
        Integer responseDeadlineDays,
        Boolean active,
        LocalDateTime createdAt,
        List<AssessmentTemplateQuestionResponseDTO> questions
) {}
