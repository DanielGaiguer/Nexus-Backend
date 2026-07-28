package com.main.nexus.dto;

import com.main.nexus.model.enums.MatchOutcome;

public record MatchStatusCheckRequestDTO(
        MatchOutcome outcome
) {}
