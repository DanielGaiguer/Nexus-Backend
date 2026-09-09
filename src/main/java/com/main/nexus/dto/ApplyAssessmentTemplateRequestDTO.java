package com.main.nexus.dto;

// Aplicar um molde a uma vaga. `title` e `responseDeadlineDays` são sobrescritas opcionais --
// null usa o que está no molde.
//
// Nada aqui cria vínculo: a etapa gerada é uma cópia autônoma (ver
// AssessmentTemplateService.applyToProject).
public record ApplyAssessmentTemplateRequestDTO(
        Long projectId,
        String title,
        Integer responseDeadlineDays
) {}
