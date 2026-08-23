package com.main.nexus.dto;

import com.main.nexus.model.enums.StatusMatch;

/**
 * Status do match (se existir) entre um profissional e um projeto específico
 * -- usado pelos diálogos de comparação pra decidir se mostram o botão
 * "Demonstrar interesse" (só faz sentido quando não há nenhum
 * envolvimento ainda: status null ou WAITING).
 */
public record MatchStatusDTO(StatusMatch status) {
}
