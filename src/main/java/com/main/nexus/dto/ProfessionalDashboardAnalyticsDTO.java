package com.main.nexus.dto;

import java.util.List;

public record ProfessionalDashboardAnalyticsDTO(
        MatchSummaryDTO matchSummary,
        List<MonthlyMatchDTO> matchesPerMonth,
        List<ScoreDistributionDTO> scoreDistribution,
        List<CompanyAcceptanceRateDTO> acceptanceRatePerCompany,
        List<SkillDemandDTO> mostRequiredSkills,
        ReputationSummaryDTO reputationSummary,
        List<SkillGapDTO> skillGaps,
        List<SoftSkillFeedbackDTO> softSkillFeedback
) {}
