package com.main.nexus.controller;

import com.main.nexus.dto.MatchResponseDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ProjectRequestDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.GeolocationService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProjectResponseAssembler;
import com.main.nexus.service.ProjectService;
import com.main.nexus.service.SkillService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Autowired
    private ProjectResponseAssembler projectResponseAssembler;

    @GetMapping("/skills")
    public ResponseEntity<?> listSkills() {
        return ResponseEntity.ok(skillService.findAll());
    }

    @GetMapping("/previous")
    public ResponseEntity<List<MatchResponseDTO>> getPreviousProjects() {
        Company company = getLoggedCompany();
        List<Match> matches = matchService.getPreviousProjectsByCompany(company.getId());
        return ResponseEntity.ok(matches.stream().map(m -> toMatchResponseDTO(m, company)).toList());
    }

    // Interesses que profissionais enviaram pras vagas da empresa, ainda aguardando resposta.
    @GetMapping("/matches/received")
    public ResponseEntity<List<MatchResponseDTO>> getReceivedInterests() {
        Company company = getLoggedCompany();
        List<Match> matches = matchService.getReceivedInterestsForCompany(company.getId());
        return ResponseEntity.ok(matches.stream().map(m -> toMatchResponseDTO(m, company)).toList());
    }

    // Convites que a empresa enviou (a partir do ranking), ainda aguardando resposta do profissional.
    @GetMapping("/matches/sent")
    public ResponseEntity<List<MatchResponseDTO>> getSentInvites() {
        Company company = getLoggedCompany();
        List<Match> matches = matchService.getSentInvitesForCompany(company.getId());
        return ResponseEntity.ok(matches.stream().map(m -> toMatchResponseDTO(m, company)).toList());
    }

    @GetMapping("/matches/confirmed")
    public ResponseEntity<List<MatchResponseDTO>> getConfirmedMatches() {
        Company company = getLoggedCompany();
        List<Match> matches = matchService.getConfirmedMatchesForCompany(company.getId());
        return ResponseEntity.ok(matches.stream().map(m -> toMatchResponseDTO(m, company)).toList());
    }

    @GetMapping("/matches/rejected")
    public ResponseEntity<List<MatchResponseDTO>> getRejectedMatches() {
        Company company = getLoggedCompany();
        List<Match> matches = matchService.getRejectedMatchesForCompany(company.getId());
        return ResponseEntity.ok(matches.stream().map(m -> toMatchResponseDTO(m, company)).toList());
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> listMyProjects() {
        Company company = getLoggedCompany();
        return ResponseEntity.ok(
                projectService.findByCompany(company)
                        .stream()
                        .map(p -> toResponseDTO(p, company))
                        .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> findById(@PathVariable Long id) {
        Company company = getLoggedCompany();
        Project project = projectService.findByIdAndCompany(id, company.getId());
        return ResponseEntity.ok(toResponseDTO(project, company));
    }

    @PostMapping
    public ResponseEntity<ProjectResponseDTO> create(@RequestBody ProjectRequestDTO request) {
        Company company = getLoggedCompany();

        Project project = new Project();
        project.setCompany(company);
        populate(project, request);

        return ResponseEntity.ok(toResponseDTO(projectService.save(project), company));
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

        return ResponseEntity.ok(toResponseDTO(projectService.update(existing), company));
    }

    // Método de populate centralizado

    private void populate(Project project, ProjectRequestDTO r) {
        project.setTitle(r.title());
        project.setDescription(r.description());
        project.setWorkMode(r.workMode());
        project.setType(r.type());
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
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() == 404) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                            "CEP not found. Please check the ZIP code and try again.");
                }
                if (e.getStatusCode().value() == 400) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "CEP has an invalid format. Expected format: 00000-000 or 00000000.");
                }
                throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                        "Could not validate the ZIP code at this moment. Please try again in a few seconds.");
            }
        } else {
            // limpa a localização própria se o CEP foi removido 
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

        // Visibilidade — nulo é tratado como "visível/aberto" (nosso frontend sempre envia
        // um valor explícito; um cliente de API externo que não conheça o campo mantém o
        // comportamento anterior à feature)
        project.setVisibleToCompanies(r.visibleToCompanies() == null || r.visibleToCompanies());

        Set<UserType> salaryVisibleTo = new HashSet<>();
        if (r.salaryVisibleToCompanies() == null || r.salaryVisibleToProfessionals()) {
            salaryVisibleTo.add(UserType.PROFESSIONAL);
        }
        if (r.salaryVisibleToCompanies() == null || r.salaryVisibleToCompanies()) {
            salaryVisibleTo.add(UserType.COMPANY);
        }
        project.setSalaryVisibleTo(salaryVisibleTo);
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
        return ResponseEntity.ok(toResponseDTO(project, company));
    }

    @PutMapping("/{id}/resume")
    public ResponseEntity<ProjectResponseDTO> resumeProject(
            @PathVariable Long id,
            @RequestParam Integer additionalPositions) {
        Company company = getLoggedCompany();
        Project project = projectService.resumePausedProject(id, company.getId(), additionalPositions);
        return ResponseEntity.ok(toResponseDTO(project, company));
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
        // garante que so o dono do projeto vê o ranking dele
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

        return ResponseEntity.ok(matches.stream().map(m -> toMatchResponseDTO(m, company)).toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        Company company = getLoggedCompany();
        projectService.delete(id, company.getId());
        return ResponseEntity.ok("Project deleted.");
    }

    // O dono do projeto sempre tem acesso total — a lista de skills embutida não usa mapper
    // de mascaramento porque não há o que mascarar aqui.
    private ProjectResponseDTO toResponseDTO(Project p, Company loggedCompany) {
        return projectResponseAssembler.toDTO(
                p, ProjectResponseAssembler.Viewer.company(loggedCompany.getId()), null);
    }

    private MatchResponseDTO toMatchResponseDTO(Match m, Company loggedCompany) {
        Professional professional = m.getProfessional();
        return new MatchResponseDTO(
                m.getId(),
                m.getMatchScore(),
                m.getCompanyStatus(),
                m.getProfessionalStatus(),
                m.getStatus(),
                m.getCreatedAt(),
                toResponseDTO(m.getProject(), loggedCompany),
                new ProfessionalSummaryDTO(
                        professional.getId(),
                        professional.getName(),
                        professional.getPhone(),
                        professional.getReputation(),
                        professional.getProfilePhotoUrl(),
                        professional.getSkills().stream().map(Skill::getName).toList()
                ),
                matchService.getScoreBreakdown(professional, m.getProject(), m),
                m.getActive()
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