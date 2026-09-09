package com.main.nexus.controller;

import com.main.nexus.dto.ApplyAssessmentTemplateRequestDTO;
import com.main.nexus.dto.AssessmentTemplateRequestDTO;
import com.main.nexus.dto.AssessmentTemplateResponseDTO;
import com.main.nexus.dto.ScreeningStageResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.AssessmentTemplate;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.service.AssessmentTemplateService;
import com.main.nexus.service.CompanyAccessService;
import com.main.nexus.service.ScreeningQuestionnaireService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Biblioteca de testes reutilizáveis da empresa. Sem requireOwner em nada aqui -- é conteúdo de
// recrutamento, que qualquer membro ACTIVE gerencia (o guard de papel vive em
// AssessmentTemplateService, não espalhado nos endpoints).
//
// Não existe DELETE: aposentar um molde é PUT /{id}/active?active=false. Etapas já geradas
// apontam pra ele em ScreeningStage.sourceTemplateId, e apagar a linha deixaria esse rastro
// órfão (ver AssessmentTemplate).
@RestController
@RequestMapping("/api/assessment-templates")
public class AssessmentTemplateController {

    @Autowired
    private AssessmentTemplateService assessmentTemplateService;

    @Autowired
    private ScreeningQuestionnaireService screeningQuestionnaireService;

    @Autowired
    private CompanyAccessService companyAccessService;

    @GetMapping
    public ResponseEntity<List<AssessmentTemplateResponseDTO>> list(
            @RequestParam(required = false, defaultValue = "false") boolean applicableOnly) {
        Long companyId = loggedAccess().company().getId();
        List<AssessmentTemplate> templates = applicableOnly
                ? assessmentTemplateService.listApplicableForCompany(companyId)
                : assessmentTemplateService.listForCompany(companyId);
        return ResponseEntity.ok(
                templates.stream().map(assessmentTemplateService::toResponseDTO).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssessmentTemplateResponseDTO> findById(@PathVariable Long id) {
        AssessmentTemplate template =
                assessmentTemplateService.getForCompany(id, loggedAccess().company().getId());
        return ResponseEntity.ok(assessmentTemplateService.toResponseDTO(template));
    }

    @PostMapping
    public ResponseEntity<AssessmentTemplateResponseDTO> create(
            @RequestBody AssessmentTemplateRequestDTO request) {
        AssessmentTemplate template = assessmentTemplateService.create(request, loggedAccess());
        return ResponseEntity.ok(assessmentTemplateService.toResponseDTO(template));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssessmentTemplateResponseDTO> update(
            @PathVariable Long id, @RequestBody AssessmentTemplateRequestDTO request) {
        AssessmentTemplate template = assessmentTemplateService.update(id, request, loggedAccess());
        return ResponseEntity.ok(assessmentTemplateService.toResponseDTO(template));
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<AssessmentTemplateResponseDTO> setActive(
            @PathVariable Long id, @RequestParam boolean active) {
        AssessmentTemplate template = assessmentTemplateService.setActive(id, active, loggedAccess());
        return ResponseEntity.ok(assessmentTemplateService.toResponseDTO(template));
    }

    // Instancia uma ScreeningStage nova na vaga, COPIANDO as questões do molde. A etapa nasce
    // independente: editar o molde depois não a altera (ver AssessmentTemplateService).
    @PostMapping("/{id}/apply")
    public ResponseEntity<ScreeningStageResponseDTO> apply(
            @PathVariable Long id, @RequestBody ApplyAssessmentTemplateRequestDTO request) {
        ScreeningStage stage = assessmentTemplateService.applyToProject(id, request, loggedAccess());
        return ResponseEntity.ok(screeningQuestionnaireService.toStageResponseDTO(stage));
    }

    // UTILITÁRIOS DE IDENTIDADE — mesmo padrão de ScreeningQuestionnaireController

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private CompanyAccessService.CompanyAccess loggedAccess() {
        return companyAccessService.resolve(getLoggedUser());
    }
}
