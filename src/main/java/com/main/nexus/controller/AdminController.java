package com.main.nexus.controller;

import com.main.nexus.dto.AdminDashboardDTO;
import com.main.nexus.dto.CompanyDashboardDTO;
import com.main.nexus.dto.CompanyProfileDTO;
import com.main.nexus.dto.MatchResponseDTO;
import com.main.nexus.dto.PreviousProjectRequestDTO;
import com.main.nexus.dto.ProfessionalDashboardDTO;
import com.main.nexus.dto.ProfessionalProfileDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.dto.UserSummaryDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.UserRepository;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.PreviousProjectService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ProfileCompletionService;
import com.main.nexus.service.SkillService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SkillService skillService;
    
    @Autowired
    private ProfessionalRepository professionalRepository;
    
    @Autowired
    private CompanyRepository companyRepository;
    
    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PreviousProjectService previousProjectService;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private ProfileCompletionService profileCompletionService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> dashboard() {
        long totalProfessionals = professionalRepository.count();
        long totalCompanies    = companyRepository.findAll().size();
        long totalUsers        =  totalCompanies + totalProfessionals;
        long totalProjects     = projectRepository.count();
        long totalOpenProjects = projectRepository.findByStatus(ProjectStatus.OPEN).size();
        long totalMatches      = matchRepository.count();
        long confirmedMatches  = matchService.countConfirmedMatches();
        Double avgScore        = matchRepository.findAverageMatchScore();
        int pendingCompanies   = companyService.findPending().size();

        return ResponseEntity.ok(new AdminDashboardDTO(
                totalUsers,
                totalProfessionals,
                totalCompanies,
                totalProjects,
                totalOpenProjects,
                totalMatches,
                confirmedMatches,
                avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0,
                pendingCompanies
        ));
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponseDTO>> listAllProjects() {
        List<ProjectResponseDTO> projects = projectRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/companies/pending")
    public ResponseEntity<?> pendingCompanies() {
        return ResponseEntity.ok(companyService.findPending());
    }

    @PostMapping("/companies/{id}/approve")
    public ResponseEntity<String> approveCompany(@PathVariable Long id) {
        companyService.approve(id);
        return ResponseEntity.ok("Company approved.");
    }

    @PostMapping("/companies/{id}/reject")
    public ResponseEntity<String> rejectCompany(@PathVariable Long id) {
        companyService.reject(id);
        return ResponseEntity.ok("Company rejected.");
    }

    @GetMapping("/skills")
    public ResponseEntity<?> listSkills() {
        return ResponseEntity.ok(skillService.findAll());
    }

    @PostMapping("/skills")
    public ResponseEntity<String> createSkill(
            @RequestParam String name,
            @RequestParam(required = false) String category) {
        skillService.create(name, category);
        return ResponseEntity.ok("Skill created.");
    }

    @DeleteMapping("/skills/{id}")
    public ResponseEntity<String> deleteSkill(@PathVariable Long id) {
        skillService.delete(id);
        return ResponseEntity.ok("Skill deleted.");
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDTO>> listUsers() {
        List<UserSummaryDTO> users = userRepository.findAll()
                .stream()
                .map(u -> new UserSummaryDTO(
                        u.getId(),
                        u.getEmail(),
                        u.getType().name(),
                        u.getActive()))
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users/{id}/toggle")
    public ResponseEntity<String> toggleUser(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setActive(!user.getActive());
            userRepository.save(user);
        });
        return ResponseEntity.ok("User status updated.");
    }

    @GetMapping("/professionals/{id}/matches")
    public ResponseEntity<List<MatchResponseDTO>> getProfessionalMatches(@PathVariable Long id) {
        List<MatchResponseDTO> matches = matchService.getMatchesByProfessional(id)
                .stream()
                .map(this::toMatchResponseDTO)
                .toList();
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/professionals/{id}/profile")
    public ResponseEntity<ProfessionalProfileDTO> getProfessionalProfile(@PathVariable Long id) {
        Professional professional = professionalService.findById(id);
        return ResponseEntity.ok(toProfessionalProfileDTO(professional));
    }

    @GetMapping("/professionals/{id}/projects")
    public ResponseEntity<List<PreviousProjectRequestDTO>> getProfessionalProjects(@PathVariable Long id) {
        return ResponseEntity.ok(previousProjectService.findByProfessional(id));
    }

    @GetMapping("/professionals/{id}/dashboard")
    public ResponseEntity<ProfessionalDashboardDTO> getProfessionalDashboard(@PathVariable Long id) {
        Professional professional = professionalService.findById(id);
        int totalProjects = previousProjectService.findByProfessional(id).size();
        long totalMatches = matchService.getMatchesByProfessional(id)
                .stream()
                .filter(m -> m.getStatus() == com.main.nexus.model.enums.StatusMatch.MATCHED)
                .count();
        return ResponseEntity.ok(new ProfessionalDashboardDTO(
                toProfessionalProfileDTO(professional),
                totalProjects,
                totalMatches
        ));
    }

    @GetMapping("/companies/{id}/profile")
    public ResponseEntity<CompanyProfileDTO> getCompanyProfile(@PathVariable Long id) {
        Company company = companyService.findById(id);
        return ResponseEntity.ok(toCompanyProfileDTO(company));
    }

    @GetMapping("/companies/{id}/dashboard")
    public ResponseEntity<CompanyDashboardDTO> getCompanyDashboard(@PathVariable Long id) {
        Company company = companyService.findById(id);
        int totalProjects = projectRepository.findByCompanyId(id).size();
        long totalMatches = matchService.countConfirmedMatchesByCompany(id);
        return ResponseEntity.ok(new CompanyDashboardDTO(
                toCompanyProfileDTO(company),
                totalProjects,
                totalMatches
        ));
    }

    @GetMapping("/companies/{id}/projects")
    public ResponseEntity<List<ProjectResponseDTO>> getCompanyProjects(@PathVariable Long id) {
        List<ProjectResponseDTO> projects = projectRepository.findByCompanyId(id)
                .stream()
                .map(this::toResponseDTO)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/companies/{id}/matches")
    public ResponseEntity<List<MatchResponseDTO>> getCompanyMatches(@PathVariable Long id) {
        List<MatchResponseDTO> matches = matchService.getMatchesByCompany(id)
                .stream()
                .map(this::toMatchResponseDTO)
                .toList();
        return ResponseEntity.ok(matches);
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
                )
        );
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

    private CompanyProfileDTO toCompanyProfileDTO(Company c) {
        return new CompanyProfileDTO(
                c.getId(),
                c.getCompanyName(),
                c.getUser().getEmail(),
                c.getTaxId(),
                c.getPhone(),
                c.getCity(),
                c.getUf(),
                c.getCep(),
                c.getDescription(),
                c.getReputation(),
                c.getLatitude(),
                c.getLongitude(),
                c.getStatus().name(),
                c.getProfilePhotoUrl(),
                c.getLinkedinUrl()
        );
    }

    private ProfessionalProfileDTO toProfessionalProfileDTO(Professional p) {
        List<String> missing = profileCompletionService.getMissingFields(p);
        return new ProfessionalProfileDTO(
                p.getId(),
                p.getName(),
                p.getUser().getEmail(),
                p.getPhone(),
                p.getCity(),
                p.getUf(),
                p.getCep(),
                p.getAvailable(),
                p.getReputation(),
                p.getLatitude(),
                p.getLongitude(),
                p.getSkills().stream().map(Skill::getName).toList(),
                p.getPreferredTypes(),
                p.getExperienceLevel(),
                p.getProfilePhotoUrl(),
                p.getPreferredOpportunityTypes(),
                p.getExpectedSalaryCLT(),
                p.getExpectedSalaryPJ(),
                p.getFreelanceMinExpectation(),
                p.getFreelanceMaxExpectation(),
                missing.isEmpty(),
                missing,
                p.getLinkedinUrl()
        );
    }
}