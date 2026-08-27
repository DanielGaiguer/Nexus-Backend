package com.main.nexus.dto;

public record ScreeningAnswerSubmitDTO(
        Long questionId,
        Integer selectedOptionIndex,
        String essayText,
        Integer timeSpentSeconds
) {}
