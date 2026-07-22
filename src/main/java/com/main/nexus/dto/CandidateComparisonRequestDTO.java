package com.main.nexus.dto;

import java.util.List;

public record CandidateComparisonRequestDTO(
        Long projectId,
        List<Long> matchIds
) {}