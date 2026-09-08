package com.main.nexus.dto;

// Upsert do parecer do avaliador logado. `rating` obrigatório, 1 a 5. `comment` opcional.
public record CandidateEvaluationRequestDTO(
        Integer rating,
        String comment
) {}
