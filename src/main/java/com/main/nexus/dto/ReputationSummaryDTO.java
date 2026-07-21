// ReputationSummaryDTO.java
package com.main.nexus.dto;

public record ReputationSummaryDTO(
        Double overallReputation,
        Double confidenceScore,
        Integer totalReviews,
        Double satisfactionAverage,
        Double recommendationRate,
        Double communication,
        Double reliability,
        Double punctuality,
        Double professionalism
) {}