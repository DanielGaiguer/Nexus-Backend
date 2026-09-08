package com.main.nexus.dto;

// `id` nulo = coluna nova; preenchido = edita a existente no lugar. A ordem das colunas no board
// é a ordem desta lista (igual ao mergeStages de ScreeningStage). Colunas existentes que não
// vierem na lista são desativadas (se tiverem card parado nelas ou histórico) ou apagadas.
public record PipelineStageRequestDTO(
        Long id,
        String name
) {}
