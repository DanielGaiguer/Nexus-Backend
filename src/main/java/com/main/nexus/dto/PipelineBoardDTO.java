package com.main.nexus.dto;

import java.util.List;

// Board pronto para renderizar: as colunas intermediárias configuráveis (`stages`, ativas e
// arquivadas, na ordem) e todos os cards "em jogo" (pipelineStage != null OU já numa coluna
// terminal). Cada card carrega sua coluna efetiva -- o frontend agrupa por `card.column`.
// As colunas terminais "Contratado"/"Reprovado" não aparecem em `stages` (não são PipelineStage);
// o frontend as renderiza fixas e distribui os cards com column.kind HIRED/REJECTED nelas.
public record PipelineBoardDTO(
        Long projectId,
        List<PipelineStageDTO> stages,
        List<PipelineCardDTO> cards
) {}
