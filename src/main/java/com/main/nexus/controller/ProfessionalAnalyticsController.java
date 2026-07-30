package com.main.nexus.controller;

import com.main.nexus.dto.ProfessionalDashboardAnalyticsDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Professional;
import com.main.nexus.service.ProfessionalAnalyticsService;
import com.main.nexus.service.ProfessionalService;
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
public class ProfessionalAnalyticsController {

    @Autowired
    private ProfessionalAnalyticsService analyticsService;

    @Autowired
    private ProfessionalService professionalService;

    // Dashboard do profissional autenticado 

    @GetMapping("/professional/dashboard")
    public ResponseEntity<ProfessionalDashboardAnalyticsDTO> getMyDashboard() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional profile not found."));

        return ResponseEntity.ok(analyticsService.buildDashboard(professional.getId()));
    }

    // Dashboard de qualquer profissional, somente Admin 

    @GetMapping("/professional/{professionalId}/dashboard")
    public ResponseEntity<ProfessionalDashboardAnalyticsDTO> getDashboardByAdmin(
            @PathVariable Long professionalId) {
        return ResponseEntity.ok(analyticsService.buildDashboard(professionalId));
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
