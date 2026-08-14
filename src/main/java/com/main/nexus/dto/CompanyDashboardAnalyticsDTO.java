
package com.main.nexus.dto;

import java.util.List;

public record CompanyDashboardAnalyticsDTO(
        MatchSummaryDTO matchSummary,
        List<MonthlyMatchDTO> matchesPerMonth,
        List<ScoreDistributionDTO> scoreDistribution,
        List<ProjectAcceptanceRateDTO> acceptanceRatePerProject,
        List<SkillDemandDTO> mostRequiredSkills,
        ReputationSummaryDTO reputationSummary,

        // Média de dias entre a publicação da oportunidade e o 1º match confirmado —
        // null se a empresa ainda não teve nenhum match MATCHED.
        Double avgDaysToFirstMatch
) {}