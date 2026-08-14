package com.main.nexus.dto;

import java.util.List;

public record AdminDashboardDTO(
        Long totalUsers,
        Long totalProfessionals,
        Long totalCompanies,
        Long totalProjects,
        Long totalOpenProjects,
        Long totalMatches,
        Long totalConfirmedMatches,
        // % de matches que chegaram a MATCHED em relação ao total de matches existentes
        // (todos os status) — substitui o antigo score médio de compatibilidade.
        Double matchConversionRate,
        Integer pendingCompanies,
        // Matches por mês dos últimos 12 meses (todo o sistema) — gráfico de evolução do painel.
        List<MonthlyMatchCountDTO> monthlyMatches
) {}