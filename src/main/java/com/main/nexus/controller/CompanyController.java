package com.main.nexus.controller;

import com.main.nexus.dto.CompanyProfileDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MatchService matchService;

    // --- Perfil ---

    @GetMapping("/profile")
    public ResponseEntity<CompanyProfileDTO> getProfile() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(toProfileDTO(company));
    }

    @PutMapping("/profile")
    public ResponseEntity<CompanyProfileDTO> updateProfile(
            @RequestBody CompanyProfileDTO request) {
        UserDTO logged = getLoggedUser();
        Company existing = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        existing.setCompanyName(request.companyName());
        existing.setPhone(request.phone());
        existing.setCity(request.city());
        existing.setDescription(request.description());
        existing.setLatitude(request.latitude());
        existing.setLongitude(request.longitude());

        companyService.update(existing);
        return ResponseEntity.ok(toProfileDTO(existing));
    }

    // --- Dashboard resumido ---

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(new java.util.HashMap<>() {{
            put("company", toProfileDTO(company));
            put("totalProjects", projectService.findByCompany(company).size());
            put("totalMatches", matchService.countConfirmedMatchesByCompany(company.getId()));
        }});
    }

    // --- Utilitários ---

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private CompanyProfileDTO toProfileDTO(Company c) {
        return new CompanyProfileDTO(
                c.getId(),
                c.getCompanyName(),
                c.getUser().getEmail(),
                c.getTaxId(),
                c.getPhone(),
                c.getCity(),
                c.getDescription(),
                c.getReputation(),
                c.getLatitude(),
                c.getLongitude(),
                c.getStatus().name()
        );
    }
}