package com.main.nexus.dto;

// Corpo de POST /api/screening-invitations/{id}/approve e /reprove -- comentário livre e
// opcional, sem nota por questão (a decisão é binária pela etapa inteira).
public record ScreeningStageDecisionRequestDTO(
        String comment
) {}
