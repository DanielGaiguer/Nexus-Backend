package com.main.nexus.dto;

import java.util.List;

// Panorama das confirmações pós-contratação de uma empresa, para a avaliação
// manual do Admin. `suspicious` é calculado (>= 3 CLOSED_NO_CHARGE, ou >= 3 /
// >= 40% em VALUE_DIVERGENCE + NO_RESPONSE); as contagens aparecem sempre,
// independente do flag.
public record AdminCompanyConfirmationOverviewDTO(
        Long companyId,
        String companyName,
        boolean underObservation,
        boolean suspicious,
        int totalConfirmations,
        int awaitingCount,
        int confirmedCount,
        int pendingReviewCount,
        int closedNoChargeCount,
        int closedUnresolvedCount,
        int valueDivergenceCount,
        int noResponseCount,
        int completionDisagreementCount,
        int unreviewedCount,
        List<AdminMatchConfirmationDTO> confirmations
) {}
