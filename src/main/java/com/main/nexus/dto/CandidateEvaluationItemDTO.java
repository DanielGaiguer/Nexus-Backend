package com.main.nexus.dto;

import java.time.LocalDateTime;

// Um parecer individual dentro do consolidado. `evaluatorLabel` vem SEMPRE do snapshot
// persistido. `mine` marca o parecer do avaliador que está pedindo.
public record CandidateEvaluationItemDTO(
        Long evaluatorMemberId,
        Long evaluatorUserId,
        String evaluatorLabel,
        Integer rating,
        String comment,
        boolean mine,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
