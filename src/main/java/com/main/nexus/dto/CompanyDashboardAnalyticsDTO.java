
package com.main.nexus.dto;

import java.util.List;

public record CompanyDashboardAnalyticsDTO(
        MatchSummaryDTO matchSummary,
        List<MonthlyMatchDTO> matchesPerMonth,
        List<ScoreDistributionDTO> scoreDistribution,
        List<ProjectAcceptanceRateDTO> acceptanceRatePerProject,
        List<SkillDemandDTO> mostRequiredSkills,
        ReputationSummaryDTO reputationSummary,
        List<SoftSkillFeedbackDTO> softSkillFeedback,
        List<ProjectStatusDistributionDTO> projectStatusDistribution
) {}