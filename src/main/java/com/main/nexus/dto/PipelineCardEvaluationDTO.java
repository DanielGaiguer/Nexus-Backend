package com.main.nexus.dto;

// O scorecard humano do card, resumido: média (1-5, uma casa decimal) + nº de pareceres.
// DELIBERADAMENTE um objeto separado de PipelineCardDTO.matchScore (o score algorítmico 0-100) --
// os dois números não se misturam no mesmo campo. `average` é null quando count == 0.
public record PipelineCardEvaluationDTO(
        Double average,
        int count
) {}
