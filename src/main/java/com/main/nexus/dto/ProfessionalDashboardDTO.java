
package com.main.nexus.dto;

public record ProfessionalDashboardDTO(
        ProfessionalProfileDTO professional,
        int totalProjects,
        long totalMatches
) {}
