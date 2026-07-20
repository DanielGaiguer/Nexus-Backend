package com.main.nexus.dto;

public record AdminDashboardDTO(
        Long totalUsers,
        Long totalProfessionals,
        Long totalCompanies,
        Long totalProjects,
        Long totalOpenProjects,
        Long totalMatches,
        Long totalConfirmedMatches,
        Double averageMatchScore,
        Integer pendingCompanies
) {}