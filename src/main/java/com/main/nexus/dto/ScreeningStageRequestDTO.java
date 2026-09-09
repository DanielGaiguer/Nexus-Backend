package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningStageKind;
import java.util.List;

// `id` nulo = etapa nova; preenchido = edita a existente no lugar (ver
// ScreeningQuestionnaireService.mergeStages).
public record ScreeningStageRequestDTO(
        Long id,
        // null = QUESTIONS (compatibilidade com qualquer cliente que ainda nao mande o campo).
        // BEHAVIORAL ignora `questions` -- os itens vem do banco fixo de plataforma, e mandar
        // pergunta customizada numa etapa dessas e 400 (ver ScreeningQuestionnaireService).
        ScreeningStageKind kind,
        // De qual AssessmentTemplate esta etapa saiu, quando ela foi montada aplicando um molde
        // pelo formulário da vaga (o front copia as questões localmente e salva tudo junto, em
        // vez de chamar /apply no meio da edição -- que criaria a etapa no banco enquanto o
        // formulário segura estado velho). Só é lido em etapa NOVA: uma edição não reescreve a
        // procedência de uma etapa que já existe.
        Long sourceTemplateId,
        String title,
        String instructions,
        Integer responseDeadlineDays,
        List<ScreeningQuestionRequestDTO> questions
) {}
