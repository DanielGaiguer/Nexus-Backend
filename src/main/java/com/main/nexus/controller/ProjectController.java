package com.main.nexus.controller;

import com.main.nexus.dto.ProjectRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Project;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProjectService;
import com.main.nexus.service.SkillService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private SkillService skillService;

    // --- Listagem ---

    @GetMapping
    public ResponseEntity<List<Project>> listMyProjects() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        return ResponseEntity.ok(projectService.findByCompany(company));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> findById(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.findById(id));
    }

    // --- Criar ---

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody ProjectRequestDTO request) {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Project project = new Project();
        project.setCompany(company);
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setMinimumBudget(request.minimumBudget());
        project.setMaximumBudget(request.maximumBudget());
        project.setDeadline(request.deadline());
        project.setWorkMode(request.workMode());

        if (request.skillIds() != null) {
            project.setRequiredSkills(skillService.findAllById(request.skillIds()));
        }

        return ResponseEntity.ok(projectService.save(project));
    }

    // --- Editar ---

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(
            @PathVariable Long id,
            @RequestBody ProjectRequestDTO request) {
        Project existing = projectService.findById(id);
        existing.setTitle(request.title());
        existing.setDescription(request.description());
        existing.setMinimumBudget(request.minimumBudget());
        existing.setMaximumBudget(request.maximumBudget());
        existing.setDeadline(request.deadline());
        existing.setWorkMode(request.workMode());

        if (request.skillIds() != null) {
            existing.setRequiredSkills(skillService.findAllById(request.skillIds()));
        }

        return ResponseEntity.ok(projectService.update(existing));
    }

    // --- Fechar ---

    @PutMapping("/{id}/close")
    public ResponseEntity<String> closeProject(@PathVariable Long id) {
        projectService.closeProject(id);
        return ResponseEntity.ok("Project closed.");
    }

    // --- Ranking de profissionais ---

    @GetMapping("/{id}/ranking")
    public ResponseEntity<?> getRanking(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getRankingByProject(id));
    }

    // --- Empresa demonstra interesse ---

    @PostMapping("/{projectId}/interest/{matchId}")
    public ResponseEntity<String> showInterest(
            @PathVariable Long projectId,
            @PathVariable Long matchId) {
        matchService.companyShowsInterest(matchId);
        return ResponseEntity.ok("Interest sent to professional.");
    }

    // --- Deletar ---

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.ok("Project deleted.");
    }

    // --- Utilitários ---

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}