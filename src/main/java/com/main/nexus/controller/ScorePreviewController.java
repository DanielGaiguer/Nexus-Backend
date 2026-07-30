package com.main.nexus.controller;

import com.main.nexus.dto.ScorePreviewResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ScorePreviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/score")
public class ScorePreviewController {

    @Autowired
    private ScorePreviewService scorePreviewService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ProfessionalService professionalService;

    // Empresa consultando o score de um profissional em um dos seus projetos
    @GetMapping("/preview")
    public ResponseEntity<ScorePreviewResponseDTO> preview(
            @RequestParam Long professionalId,
            @RequestParam Long projectId) {

        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found."));

        return ResponseEntity.ok(
                scorePreviewService.previewForCompany(company.getId(), professionalId, projectId));
    }

    // Profissional consultando seu proprio score em uma vaga/projeto
    @GetMapping("/preview/self")
    public ResponseEntity<ScorePreviewResponseDTO> previewSelf(@RequestParam Long projectId) {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found."));

        return ResponseEntity.ok(
                scorePreviewService.previewForProfessional(professional.getId(), projectId));
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
