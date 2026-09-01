package com.main.nexus.dto;

// Uma linha da fila de atenção do Admin: uma empresa com confirmações que pedem
// avaliação manual (suspeita, sob observação, casos em análise ou ainda não
// revisados).
public record AdminConfirmationQueueItemDTO(
        Long companyId,
        String companyName,
        boolean underObservation,
        boolean suspicious,
        int pendingReviewCount,
        int closedNoChargeCount,
        int closedUnresolvedCount,
        int valueDivergenceCount,
        int noResponseCount,
        int completionDisagreementCount,
        int awaitingCount,
        int unreviewedCount
) {}
