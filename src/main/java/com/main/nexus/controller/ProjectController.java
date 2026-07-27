package com.main.nexus.controller;

import com.main.nexus.dto.MatchResponseDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ProjectRequestDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.GeolocationService;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    
    @Autowired
    private GeolocationService geolocationService;

    @GetMapping("/skills")
    public ResponseEntity<?> listSkills() {
        return ResponseEntity.ok(skillService.findAll());
    }

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
        populate(project, request);

        return ResponseEntity.ok(toResponseDTO(projectService.save(project)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> update(
            @PathVariable Long id,
            @RequestBody ProjectRequestDTO request) {
        Company company = getLoggedCompany();
        Project existing = projectService.findByIdAndCompany(id, company.getId());

        populate(existing, request);

        if (request.maxPositions() != null) {
            if (request.maxPositions() < existing.getFilledPositions()) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Maximum positions cannot be less than filled positions.");
            }
            existing.setMaxPositions(request.maxPositions());
        }

        return ResponseEntity.ok(toResponseDTO(projectService.update(existing)));
    }

    // ── Método de populate centralizado ──────────────────────────────────────────

    private void populate(Project project, ProjectRequestDTO r) {
        project.setTitle(r.title());
        project.setDescription(r.description());
        project.setWorkMode(r.workMode());
        project.setExperienceLevel(r.experienceLevel());
        project.setOpportunityType(r.opportunityType());

        if (r.skillIds() != null) {
            project.setRequiredSkills(skillService.findAllById(r.skillIds()));
        }
        
        if (r.cep() != null && !r.cep().isBlank()) {
            try {
                GeolocationService.AddressData address = geolocationService.resolveFromCep(r.cep());
                project.setCep(r.cep());
                project.setLatitude(address.latitude());
                project.setLongitude(address.longitude());
                project.setCity(address.city());
                project.setUf(address.state());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                        "CEP '" + r.cep() + "' is invalid or could not be resolved.");
            }
        } else {
            // Limpa a localização própria se o CEP foi removido (voltará a usar o da empresa)
            project.setCep(null);
            project.setLatitude(null);
            project.setLongitude(null);
            project.setCity(null);
            project.setUf(null);
        }

        // Campos de PROJECT
        project.setMinimumBudget(r.minimumBudget());
        project.setMaximumBudget(r.maximumBudget());
        project.setDeadline(r.deadline());

        // Campos de JOB
        project.setMonthlySalaryMin(r.monthlySalaryMin());
        project.setMonthlySalaryMax(r.monthlySalaryMax());
        project.setContractType(r.contractType());
        project.setBenefits(r.benefits());
        project.setStartDate(r.startDate());
        project.setWorkloadHoursPerWeek(r.workloadHoursPerWeek());
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<String> closeProject(@PathVariable Long id) {
        Company company = getLoggedCompany();
        projectService.closeProject(id, company.getId());
        return ResponseEntity.ok("Project closed.");
    }

    @PutMapping("/{id}/reopen")
    public ResponseEntity<ProjectResponseDTO> reopenProject(
            @PathVariable Long id,
            @RequestParam(required = false) Integer maxPositions) {
        Company company = getLoggedCompany();
        Project project = projectService.reopenProject(id, company.getId(), maxPositions);
        return ResponseEntity.ok(toResponseDTO(project));
    }

    @GetMapping("/{id}/ranking")
    public ResponseEntity<List<MatchResponseDTO>> getRanking(
            @PathVariable Long id,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Double minSalary,
            @RequestParam(required = false) Double maxSalary,
            @RequestParam(required = false) String skill) {
        Company company = getLoggedCompany();
        // Garante que só o dono do projeto vê o ranking dele
        projectService.findByIdAndCompany(id, company.getId());
        boolean hasFilters = (city != null && !city.isBlank())
                || (uf != null && !uf.isBlank())
                || (experienceLevel != null && !experienceLevel.isBlank())
                || available != null
                || minSalary != null
                || maxSalary != null
                || (skill != null && !skill.isBlank());

        List<Match> matches = hasFilters
                ? matchService.getRankingByProjectWithFilters(
                        id, city, uf, experienceLevel, available, minSalary, maxSalary, skill)
                : matchService.getRankingByProject(id);

        return ResponseEntity.ok(matches.stream().map(this::toMatchResponseDTO).toList());
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
                p.getWorkMode(),
                p.getExperienceLevel(),
                p.getStatus(),
                p.getMaxPositions(),
                p.getFilledPositions(),
                p.getCreatedAt(),
                p.getOpportunityType(),
                p.getRequiredSkills().stream()
                    .map(skill -> new SkillResponseDTO(
                            skill.getId(),
                            skill.getName(),
                            skill.getCategory()
                    ))
                    .toList(),
                p.getCompany().getId(),
                p.getCompany().getCompanyName(),

                // Localização efetiva — da vaga ou da empresa como fallback
                p.getCep() != null ? p.getCep() : p.getCompany().getCep(),
                p.getEffectiveLatitude(),
                p.getEffectiveLongitude(),
                p.getEffectiveCity(),
                p.getEffectiveUf(),

                // PROJECT
                p.getMinimumBudget(),
                p.getMaximumBudget(),
                p.getDeadline(),

                // JOB
                p.getMonthlySalaryMin(),
                p.getMonthlySalaryMax(),
                p.getContractType(),
                p.getBenefits(),
                p.getStartDate(),
                p.getWorkloadHoursPerWeek(),
                null
        );
    }

    private MatchResponseDTO toMatchResponseDTO(Match m) {
        Professional professional = m.getProfessional();
        return new MatchResponseDTO(
                m.getId(),
                m.getMatchScore(),
                m.getCompanyStatus(),
                m.getProfessionalStatus(),
                m.getStatus(),
                m.getCreatedAt(),
                toResponseDTO(m.getProject()),
                new ProfessionalSummaryDTO(
                        professional.getId(),
                        professional.getName(),
                        professional.getPhone(),
                        professional.getReputation()
                ),
                matchService.getScoreBreakdown(professional, m.getProject(), m)
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