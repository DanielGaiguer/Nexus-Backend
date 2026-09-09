package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningStageKind;
import java.util.List;

public record ScreeningStageResponseDTO(
        Long id,
        ScreeningStageKind kind,
        Integer orderIndex,
        String title,
        String instructions,
        Integer responseDeadlineDays,
        Boolean active,
        // Vem VAZIA numa etapa BEHAVIORAL: este DTO alimenta o formulario da vaga, e os itens do
        // inventario nao sao editaveis pela empresa -- despejar os 50 la so poluiria a tela e
        // arriscaria um round-trip reescrevendo o instrumento. `behavioralItemCount` diz quantos
        // itens a etapa tem, que e a unica coisa que o formulario precisa mostrar.
        List<ScreeningQuestionResponseDTO> questions,
        Integer behavioralItemCount
) {}
