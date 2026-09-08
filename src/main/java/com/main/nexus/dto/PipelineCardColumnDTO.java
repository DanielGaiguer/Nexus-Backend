package com.main.nexus.dto;

// Coluna EFETIVA de um card, já resolvida pelo backend a partir da tupla (status, active,
// companyStatus, professionalStatus, acceptedProposal, RejectionFeedback,
// MatchConfirmation.status, ausência de MatchHistory para expiração). Ver PipelineService.deriveColumn.
//
// kind:
//   - "STAGE"    -> card numa etapa intermediária; `stageId` aponta a PipelineStage. É o único
//                   kind arrastável.
//   - "HIRED"    -> coluna terminal "Contratado". `stageId` nulo.
//   - "REJECTED" -> coluna terminal "Reprovado" (inclui "Vaga encerrada"). `stageId` nulo.
// `subLabel` é o texto fino sob o título da coluna terminal, ou "Etapa arquivada" para um card
// parado numa STAGE inativa. Nulo quando não há nada a dizer.
public record PipelineCardColumnDTO(
        String kind,
        Long stageId,
        String subLabel
) {}
