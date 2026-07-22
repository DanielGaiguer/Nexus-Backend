
package com.main.nexus.dto;

public record ScoreBreakdownDTO(
        double skills,
        double budget,
        double history,
        double reputation,
        double availability,
        Double distance,
        Double experience,
        double reputationAdjustment,
        double finalScore
) {}