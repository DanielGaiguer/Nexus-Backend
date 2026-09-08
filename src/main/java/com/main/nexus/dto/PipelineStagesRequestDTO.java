package com.main.nexus.dto;

import java.util.List;

// Substitui a lista inteira de etapas intermediárias de uma vaga de uma vez -- mesma semântica de
// "replace" do ScreeningQuestionnaireRequestDTO.stages (ver PipelineService.replaceStages).
public record PipelineStagesRequestDTO(
        List<PipelineStageRequestDTO> stages
) {}
