package com.main.nexus.controller;

import com.main.nexus.dto.ScreeningQuestionnaireRequestDTO;
import com.main.nexus.dto.ScreeningQuestionnaireResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.service.CompanyAccessService;
import com.main.nexus.service.ScreeningQuestionnaireService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screening-questionnaires")
public class ScreeningQuestionnaireController {

    @Autowired
    private ScreeningQuestionnaireService screeningQuestionnaireService;

    @Autowired
    private CompanyAccessService companyAccessService;

    @PostMapping
    public ResponseEntity<ScreeningQuestionnaireResponseDTO> create(
            @RequestBody ScreeningQuestionnaireRequestDTO request) {
        ScreeningQuestionnaire questionnaire = screeningQuestionnaireService.create(request, getLoggedCompanyId());
        return ResponseEntity.ok(screeningQuestionnaireService.toResponseDTO(questionnaire));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScreeningQuestionnaireResponseDTO> update(
            @PathVariable Long id, @RequestBody ScreeningQuestionnaireRequestDTO request) {
        ScreeningQuestionnaire questionnaire = screeningQuestionnaireService.update(id, request, getLoggedCompanyId());
        return ResponseEntity.ok(screeningQuestionnaireService.toResponseDTO(questionnaire));
    }

    // 1:1 com o projeto -- devolve null (200) quando a vaga ainda não tem questionário.
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ScreeningQuestionnaireResponseDTO> forProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(
                screeningQuestionnaireService.getByProject(projectId, getLoggedCompanyId())
                        .map(screeningQuestionnaireService::toResponseDTO)
                        .orElse(null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScreeningQuestionnaireResponseDTO> findById(@PathVariable Long id) {
        ScreeningQuestionnaire questionnaire = screeningQuestionnaireService.getForCompany(id, getLoggedCompanyId());
        return ResponseEntity.ok(screeningQuestionnaireService.toResponseDTO(questionnaire));
    }

    // UTILITÁRIOS DE IDENTIDADE — mesmo padrão de MatchController/ProposalController

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private Long getLoggedCompanyId() {
        return getLoggedCompanyAccess().company().getId();
    }

    // Empresa + papel (OWNER/MEMBER) do usuário logado. O papel ainda não é lido
    // por ninguém -- disponível para o guard de papel da etapa seguinte.
    private CompanyAccessService.CompanyAccess getLoggedCompanyAccess() {
        return companyAccessService.resolve(getLoggedUser());
    }
}
