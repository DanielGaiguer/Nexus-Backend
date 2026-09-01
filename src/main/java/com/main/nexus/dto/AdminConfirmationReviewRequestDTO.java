package com.main.nexus.dto;

// Corpo de POST /api/admin/confirmations/{matchId}/review -- o Admin marca o
// caso como revisado e registra uma nota livre (opcional).
public record AdminConfirmationReviewRequestDTO(String note) {}
