package com.main.nexus.controller;

import com.main.nexus.dto.CompanyDashboardAnalyticsDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.CompanyAnalyticsService;
import com.main.nexus.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/analytics")
public class CompanyAnalyticsController {

    @Autowired
    private CompanyAnalyticsService analyticsService;

    @Autowired
    private CompanyService companyService;

    // Dashboard da empresa autenticada

    @GetMapping("/company/dashboard")
    public ResponseEntity<CompanyDashboardAnalyticsDTO> getMyDashboard() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found."));

        return ResponseEntity.ok(analyticsService.buildDashboard(company.getId()));
    }

    // Dashboard de qualquer empresa domente Admin

    @GetMapping("/company/{companyId}/dashboard")
    public ResponseEntity<CompanyDashboardAnalyticsDTO> getDashboardByAdmin(
            @PathVariable Long companyId) {
        return ResponseEntity.ok(analyticsService.buildDashboard(companyId));
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}