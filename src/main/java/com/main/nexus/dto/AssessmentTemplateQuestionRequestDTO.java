package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningQuestionType;
import java.util.List;

// Sem `id`, diferente de ScreeningQuestionRequestDTO: editar um molde substitui a lista inteira.
// Lá o id é obrigatório porque ScreeningAnswer referencia a questão por FK; aqui nada referencia
// estas linhas, então preservar identidade não compraria nada.
public record AssessmentTemplateQuestionRequestDTO(
        ScreeningQuestionType type,
        String prompt,
        List<String> options,
        Integer correctOptionIndex
) {}
