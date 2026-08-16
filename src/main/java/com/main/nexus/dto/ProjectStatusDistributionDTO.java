package com.main.nexus.dto;

import com.main.nexus.model.enums.ProjectStatus;

// Distribuição dos projetos/vagas da empresa por status atual — usado no card
// "Status de Vagas" do dashboard da empresa (conceito exclusivo de empresa: só ela
// possui projetos).
public record ProjectStatusDistributionDTO(
        String status,
        String enumValue,
        long count
) {

    public static ProjectStatusDistributionDTO of(ProjectStatus status, long count) {
        return new ProjectStatusDistributionDTO(label(status), status.name(), count);
    }

    // Mesmos rótulos em português já usados nas telas de projetos da empresa
    // (company-projects.html, company-dashboard.html) — fonte única de verdade.
    private static String label(ProjectStatus status) {
        return switch (status) {
            case OPEN -> "Aberto";
            case PAUSED -> "Pausado";
            case CLOSED -> "Encerrado";
        };
    }
}
