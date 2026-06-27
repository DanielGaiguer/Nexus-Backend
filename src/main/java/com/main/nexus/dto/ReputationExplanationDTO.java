
package com.main.nexus.dto;

public record ReputationExplanationDTO(
        Double overallScore,
        Double confidencePercent,
        Integer totalReviews,
        Double technicalCompetence,
        Double communication,
        Double reliability,
        Double punctuality,
        Double professionalism,
        Double satisfactionAverage,
        Double recommendationRate
) {}