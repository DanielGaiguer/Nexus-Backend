package com.main.nexus.dto;

import java.math.BigDecimal;

// Corpo de POST /api/admin/confirmations/{matchId}/resolve -- o Admin define o
// valor final a mao (apos contato com as partes). `note` opcional.
public record AdminResolveConfirmationRequestDTO(
        BigDecimal finalAmount,
        String note
) {}
