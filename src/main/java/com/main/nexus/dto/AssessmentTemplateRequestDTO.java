package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningStageKind;
import java.util.List;

// `kind` aceita null = QUESTIONS. Na prática é o único valor aceito hoje (ver
// AssessmentTemplateService.applyRequest) -- comportamental é conteúdo fixo de plataforma e
// vídeo é pergunta sobre a vaga, nenhum dos dois é "genérico e reaplicável".
public record AssessmentTemplateRequestDTO(
        String title,
        ScreeningStageKind kind,
        String instructions,
        Integer responseDeadlineDays,
        List<AssessmentTemplateQuestionRequestDTO> questions
) {}
