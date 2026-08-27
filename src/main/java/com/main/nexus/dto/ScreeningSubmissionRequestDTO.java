package com.main.nexus.dto;

import java.util.List;

public record ScreeningSubmissionRequestDTO(
        List<ScreeningAnswerSubmitDTO> answers,
        Integer totalTimeSpentSeconds,
        Integer tabSwitchCount
) {}
