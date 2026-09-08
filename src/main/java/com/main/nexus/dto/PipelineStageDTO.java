package com.main.nexus.dto;

// Uma coluna intermediária do board. `active=false` = arquivada: ainda exibida (com cards que
// pararam nela), mas não é destino válido de drop.
public record PipelineStageDTO(
        Long id,
        String name,
        Integer orderIndex,
        Boolean active
) {}
