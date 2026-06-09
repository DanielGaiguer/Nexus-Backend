
package com.main.nexus.dto;

public record CompanyDashboardDTO(
        CompanyProfileDTO company,
        int totalProjects,
        long totalMatches
) {}