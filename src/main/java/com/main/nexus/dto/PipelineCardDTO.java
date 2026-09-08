package com.main.nexus.dto;

import com.main.nexus.model.enums.StatusMatch;

// Um card do board. `column` já vem resolvida (etapa atual OU coluna terminal derivada).
// `draggable` é true só quando column.kind == "STAGE" (colunas terminais são read-only).
// `latestScreening` é o resultado mais recente do processo seletivo por etapas daquele
// profissional nesta vaga, se houver -- badge puramente informativo, sem interação.
//
// `matchScore` (score algorítmico 0-100) e `evaluation` (scorecard humano 1-5 + nº de pareceres)
// são DOIS números distintos, em campos separados de propósito -- nunca combine os dois.
public record PipelineCardDTO(
        Long matchId,
        ProfessionalSummaryDTO professional,
        Double matchScore,
        PipelineCardEvaluationDTO evaluation,
        StatusMatch status,
        Boolean active,
        boolean draggable,
        PipelineCardColumnDTO column,
        ScreeningInvitationSummaryDTO latestScreening
) {}
