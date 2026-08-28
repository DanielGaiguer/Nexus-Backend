package com.main.nexus.dto;

import java.time.LocalDate;
import java.util.List;

// Agregado do dashboard "Análises" da plataforma personalizada (contratante).
public record CustomPortalAnalyticsDTO(
        int rangeDays,
        long totalViews,
        long uniqueVisitors,
        long applyClicks,
        double conversionRate,       // applyClicks / totalViews * 100
        double avgSessionSeconds,
        List<DailyViews> viewsPerDay,
        List<OpportunityViews> topOpportunities,
        List<ReferrerCount> referrers
) {
    public record DailyViews(LocalDate date, long views) {}

    public record OpportunityViews(Long opportunityId, String title, long views) {}

    public record ReferrerCount(String label, long count) {}
}
