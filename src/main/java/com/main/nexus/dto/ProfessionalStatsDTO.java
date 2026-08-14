package com.main.nexus.dto;

public record ProfessionalStatsDTO(
        // Vagas OPEN em que o profissional já tem um match WAITING gerado — ou seja,
        // aparece no ranking e ainda não houve decisão de nenhum dos lados.
        long availableOpportunitiesCount
) {}
