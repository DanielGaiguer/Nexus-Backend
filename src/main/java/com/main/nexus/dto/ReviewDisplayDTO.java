package com.main.nexus.dto;

import java.time.LocalDateTime;
import java.util.List;

// Review pronta pra exibição — já traz o nome/foto de quem avaliou e os motivos
// traduzidos, então o frontend não precisa resolver nada, só renderizar.
public record ReviewDisplayDTO(
        Long id,
        int rating,
        String comment,
        List<String> positiveReasons,
        List<String> negativeReasons,
        String reviewerName,
        String reviewerPhotoUrl,
        String reviewerType,
        String opportunityTitle,
        LocalDateTime createdAt
) {}
