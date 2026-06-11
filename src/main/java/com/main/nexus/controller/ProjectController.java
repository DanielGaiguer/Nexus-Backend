package com.main.nexus.controller;

import com.main.nexus.dto.ProjectRequestDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProjectService;
import com.main.nexus.service.SkillService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
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
import org.springframework.web.server.ResponseStatusException;

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

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> listMyProjects() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company not found"));
        return ResponseEntity.ok(
                projectService.findByCompany(company)
                        .stream()
                        .map(this::toResponseDTO)
                        .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponseDTO(projectService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ProjectResponseDTO> create(@RequestBody ProjectRequestDTO request) {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company not found"));

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

        return ResponseEntity.ok(toResponseDTO(projectService.save(project)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> update(
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

        return ResponseEntity.ok(toResponseDTO(projectService.update(existing)));
    }
    
    private ProjectResponseDTO toResponseDTO(Project p) {
        return new ProjectResponseDTO(
                p.getId(),
                p.getTitle(),
                p.getDescription(),
                p.getMinimumBudget(),
                p.getMaximumBudget(),
                p.getDeadline(),
                p.getWorkMode(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getRequiredSkills().stream().map(Skill::getName).toList(),
                p.getCompany().getId(),
                p.getCompany().getCompanyName()
        );
    }
    
    @PutMapping("/{id}/close")
    public ResponseEntity<String> closeProject(@PathVariable Long id) {
        projectService.closeProject(id);
        return ResponseEntity.ok("Project closed.");
    }

    @GetMapping("/{id}/ranking")
    public ResponseEntity<?> getRanking(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getRankingByProject(id));
    }

    @PostMapping("/{projectId}/interest/{matchId}")
    public ResponseEntity<String> showInterest(
            @PathVariable Long projectId,
            @PathVariable Long matchId) {
        matchService.companyShowsInterest(matchId);
        return ResponseEntity.ok("Interest sent to professional.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.ok("Project deleted.");
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}