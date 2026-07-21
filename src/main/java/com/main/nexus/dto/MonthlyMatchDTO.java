
package com.main.nexus.dto;

public record MonthlyMatchDTO(
        int year,
        int month,
        String monthLabel,
        long totalMatches,
        long confirmedMatches,
        long rejectedMatches
) {}