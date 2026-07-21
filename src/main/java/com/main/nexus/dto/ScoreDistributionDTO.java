
package com.main.nexus.dto;

public record ScoreDistributionDTO(
        String range,       // ex: "0-20", "20-40", "40-60", "60-80", "80-100"
        long count,
        double percentage
) {}