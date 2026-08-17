package com.main.nexus.dto;

import java.util.List;

public record ReviewPageDTO(
        List<ReviewDisplayDTO> reviews,
        long totalReviews,
        double averageRating
) {}
