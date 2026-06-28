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
        Company company = getLoggedCompany();
        return ResponseEntity.ok(
                projectService.findByCompany(company)
                        .stream()
                        .map(this::toResponseDTO)
                        .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> findById(@PathVariable Long id) {
        Company company = getLoggedCompany();
        Project project = projectService.findByIdAndCompany(id, company.getId());
        return ResponseEntity.ok(toResponseDTO(project));
    }

    @PostMapping
    public ResponseEntity<ProjectResponseDTO> create(@RequestBody ProjectRequestDTO request) {
        Company company = getLoggedCompany();

        Project project = new Project();
        project.setCompany(company);
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setMinimumBudget(request.minimumBudget());
        project.setMaximumBudget(request.maximumBudget());
        project.setDeadline(request.deadline());
        project.setWorkMode(request.workMode());
        project.setType(request.type());
        project.setMaxPositions(request.maxPositions() != null ? request.maxPositions() : 1);

        if (request.skillIds() != null) {
            project.setRequiredSkills(skillService.findAllById(request.skillIds()));
        }

        return ResponseEntity.ok(toResponseDTO(projectService.save(project)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> update(
            @PathVariable Long id,
            @RequestBody ProjectRequestDTO request) {
        Company company = getLoggedCompany();
        Project existing = projectService.findByIdAndCompany(id, company.getId());

        existing.setTitle(request.title());
        existing.setDescription(request.description());
        existing.setMinimumBudget(request.minimumBudget());
        existing.setMaximumBudget(request.maximumBudget());
        existing.setDeadline(request.deadline());
        existing.setWorkMode(request.workMode());
        existing.setType(request.type());

        if (request.skillIds() != null) {
            existing.setRequiredSkills(skillService.findAllById(request.skillIds()));
        }

        return ResponseEntity.ok(toResponseDTO(projectService.update(existing)));
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<String> closeProject(@PathVariable Long id) {
        Company company = getLoggedCompany();
        projectService.closeProject(id, company.getId());
        return ResponseEntity.ok("Project closed.");
    }

    @GetMapping("/{id}/ranking")
    public ResponseEntity<?> getRanking(@PathVariable Long id) {
        Company company = getLoggedCompany();
        // Garante que só o dono do projeto vê o ranking dele
        projectService.findByIdAndCompany(id, company.getId());
        return ResponseEntity.ok(matchService.getRankingByProject(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        Company company = getLoggedCompany();
        projectService.delete(id, company.getId());
        return ResponseEntity.ok("Project deleted.");
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
                p.getType(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getFilledPositions(),
                p.getMaxPositions(),
                p.getRequiredSkills().stream().map(Skill::getName).toList(),
                p.getCompany().getId(),
                p.getCompany().getCompanyName()
        );
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private Company getLoggedCompany() {
        UserDTO logged = getLoggedUser();
        return companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company not found"));
    }
}