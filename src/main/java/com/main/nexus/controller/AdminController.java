package com.main.nexus.controller;

import com.main.nexus.dto.AdminDashboardDTO;
import com.main.nexus.dto.CompanyDashboardDTO;
import com.main.nexus.dto.CompanyProfileDTO;
import com.main.nexus.dto.MatchResponseDTO;
import com.main.nexus.dto.MonthlyMatchCountDTO;
import com.main.nexus.dto.PreviousProjectDTO;
import com.main.nexus.dto.ProfessionalDashboardDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.ProfessionalProfileDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.RejectCompanyRequestDTO;
import com.main.nexus.dto.SkillRequestDTO;
import com.main.nexus.dto.UserSummaryDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.CompanyType;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.UserRepository;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.EmailService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.PreviousProjectService;
import com.main.nexus.service.ProfessionalCredentialService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ProfileCompletionService;
import com.main.nexus.service.ProjectResponseAssembler;
import com.main.nexus.service.ProjectService;
import com.main.nexus.service.ProposalService;
import com.main.nexus.service.ScreeningInvitationService;
import com.main.nexus.service.SkillService;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private ProposalService proposalService;

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

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
    private ProfessionalCredentialService professionalCredentialService;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private ProfileCompletionService profileCompletionService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectResponseAssembler projectResponseAssembler;

    @Autowired
    private EmailService emailService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> dashboard() {
        long totalProfessionals = professionalRepository.count();
        long totalCompanies    = companyRepository.findAll().size();
        long totalUsers        =  totalCompanies + totalProfessionals;
        long totalProjects     = projectRepository.count();
        long totalOpenProjects = projectRepository.findByStatus(ProjectStatus.OPEN).size();
        long totalMatches      = matchRepository.count();
        long confirmedMatches  = matchService.countConfirmedMatches();
        double conversionRate  = totalMatches > 0
                ? Math.round((double) confirmedMatches / totalMatches * 1000.0) / 10.0
                : 0.0;
        int pendingCompanies   = companyService.findPending().size();

        return ResponseEntity.ok(new AdminDashboardDTO(
                totalUsers,
                totalProfessionals,
                totalCompanies,
                totalProjects,
                totalOpenProjects,
                totalMatches,
                confirmedMatches,
                conversionRate,
                pendingCompanies,
                buildMonthlyMatchCounts()
        ));
    }

    // Matches por mês dos últimos 12 meses, todo o sistema — alimenta o gráfico de evolução
    // do painel admin (antes vinha com dado fixo: tudo jogado em "Jun").
    private List<MonthlyMatchCountDTO> buildMonthlyMatchCounts() {
        LocalDateTime since = LocalDateTime.now().minusMonths(12);
        List<Object[]> raw = matchRepository.findMonthlyMatchCounts(since);

        Map<String, long[]> aggregated = new LinkedHashMap<>();
        for (Object[] row : raw) {
            int year  = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            aggregated.put(year + "-" + String.format("%02d", month), new long[]{year, month, count});
        }

        List<MonthlyMatchCountDTO> result = new ArrayList<>();
        for (long[] data : aggregated.values()) {
            int year  = (int) data[0];
            int month = (int) data[1];
            String label = Month.of(month).getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")) + "/" + year;
            result.add(new MonthlyMatchCountDTO(label, data[2]));
        }

        return result;
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponseDTO>> listAllProjects() {
        List<ProjectResponseDTO> projects = projectRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @PutMapping("/projects/{id}/close")
    public ResponseEntity<String> closeProject(@PathVariable Long id) {
        projectService.closeProjectAsAdmin(id);
        return ResponseEntity.ok("Project closed.");
    }

    @GetMapping("/companies/pending")
    public ResponseEntity<List<CompanyProfileDTO>> pendingCompanies() {
        List<CompanyProfileDTO> companies = companyService.findPending()
                .stream()
                .map(this::toCompanyProfileDTO)
                .toList();
        return ResponseEntity.ok(companies);
    }

  
    @GetMapping("/companies/latest")
    public ResponseEntity<List<CompanyProfileDTO>> latestCompanies() {
        List<CompanyProfileDTO> companies = companyRepository
                .findTop5ByStatusNotOrderByUserCreatedAtDesc(CompanyStatus.REJECTED)
                .stream()
                .map(this::toCompanyProfileDTO)
                .toList();
        return ResponseEntity.ok(companies);
    }

    @GetMapping("/companies")
    public ResponseEntity<List<CompanyProfileDTO>> listCompanies(
            @RequestParam(required = false) CompanyType type) {
        List<Company> source = type != null
                ? companyRepository.findByType(type)
                : companyRepository.findAll();
        List<CompanyProfileDTO> companies = source
                .stream()
                .map(this::toCompanyProfileDTO)
                .toList();
        return ResponseEntity.ok(companies);
    }

    @PostMapping("/companies/{id}/approve")
    public ResponseEntity<String> approveCompany(@PathVariable Long id) {
        companyService.approve(id);
        return ResponseEntity.ok("Company approved.");
    }

    @PostMapping("/companies/{id}/reject")
    public ResponseEntity<String> rejectCompany(
            @PathVariable Long id, @RequestBody RejectCompanyRequestDTO request) {
        companyService.reject(id, request.reason());
        return ResponseEntity.ok("Company rejected.");
    }

    @GetMapping("/skills")
    public ResponseEntity<?> listSkills() {
        return ResponseEntity.ok(skillService.findAll());
    }

    @PostMapping("/skills")
    public ResponseEntity<String> createSkill(@RequestBody SkillRequestDTO request) {
        skillService.create(request.name(), request.category());
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
                        resolveUserName(u),
                        u.getEmail(),
                        u.getType().name(),
                        u.getActive(),
                        resolveUserPhotoUrl(u),
                        resolveEntityId(u)))
                .toList();
        return ResponseEntity.ok(users);
    }

    // Id da linha professional/company dona desse usuário — PK própria, gerada
    // por IDENTITY independente da do usuário (não confundir com u.getId()).
    // Bug encontrado: telas de admin que montavam /admin/professional/{id} ou
    // /admin/company/{id} usando o id do User (ex.: admin-users) caíam num
    // registro professional/company qualquer que por acaso tivesse aquele
    // mesmo número de PK — professional/company e user têm sequências de id
    // independentes, só coincidem por acidente pros primeiros cadastros de
    // cada tipo (companies foram semeadas primeiro aqui, daí ids batendo só
    // até o primeiro professional).
    private Long resolveEntityId(com.main.nexus.model.User u) {
        return switch (u.getType()) {
            case PROFESSIONAL -> professionalRepository.findByUserId(u.getId())
                    .map(com.main.nexus.model.Professional::getId)
                    .orElse(null);
            case COMPANY -> companyRepository.findByUserId(u.getId())
                    .map(com.main.nexus.model.Company::getId)
                    .orElse(null);
            case ADMIN -> null;
        };
    }

    // Nome de exibição por tipo de usuário — mesmo critério do resolveName() em AuthService.
    private String resolveUserName(com.main.nexus.model.User u) {
        return switch (u.getType()) {
            case PROFESSIONAL -> professionalRepository.findByUserId(u.getId())
                    .map(com.main.nexus.model.Professional::getName)
                    .orElse(u.getEmail());
            case COMPANY -> companyRepository.findByUserId(u.getId())
                    .map(com.main.nexus.model.Company::getCompanyName)
                    .orElse(u.getEmail());
            case ADMIN -> "Admin";
        };
    }

    // Foto/logo de perfil por tipo de usuário — admin não tem esse conceito, então null
    // (o frontend cai pro avatar de iniciais nesse caso).
    private String resolveUserPhotoUrl(com.main.nexus.model.User u) {
        return switch (u.getType()) {
            case PROFESSIONAL -> professionalRepository.findByUserId(u.getId())
                    .map(com.main.nexus.model.Professional::getProfilePhotoUrl)
                    .orElse(null);
            case COMPANY -> companyRepository.findByUserId(u.getId())
                    .map(com.main.nexus.model.Company::getProfilePhotoUrl)
                    .orElse(null);
            case ADMIN -> null;
        };
    }

    @PostMapping("/users/{id}/toggle")
    public ResponseEntity<String> toggleUser(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            boolean wasActive = Boolean.TRUE.equals(user.getActive());
            user.setActive(!wasActive);
            userRepository.save(user);

            // Sem isso, o usuário só descobriria que a conta foi desativada na
            // próxima tentativa de login (403 "Account is inactive." em AuthService.login)
            if (wasActive) {
                emailService.send(
                        user.getEmail(),
                        "Sua conta foi desativada — Nexus",
                        "Olá,\n\nSua conta na plataforma Nexus foi desativada por um administrador. " +
                        "A partir de agora, você não conseguirá mais fazer login até que ela seja reativada.\n\n" +
                        "Se você acredita que isso foi um engano, entre em contato com o suporte.\n\nEquipe Nexus"
                );
            } else {
                emailService.send(
                        user.getEmail(),
                        "Sua conta foi reativada — Nexus",
                        "Olá,\n\nSua conta na plataforma Nexus foi reativada por um administrador. " +
                        "Você já pode fazer login normalmente novamente.\n\nEquipe Nexus"
                );
            }
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
    public ResponseEntity<List<PreviousProjectDTO>> getProfessionalProjects(@PathVariable Long id) {
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
        RejectionFeedback feedback = m.getStatus() == StatusMatch.REJECTED
                ? matchService.getRejectionFeedback(m.getId()) : null;
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
                        professional.getReputation(),
                        professional.getProfilePhotoUrl(),
                        professional.getSkills().stream().map(Skill::getName).toList()
                ),
                null,
                m.getActive(),
                matchService.getRejectionReasonNames(feedback),
                feedback != null ? feedback.getDescription() : null,
                m.getAcceptedProposal() != null ? proposalService.toResponseDTO(m.getAcceptedProposal()) : null,
                screeningInvitationService.getSummariesForMatch(m)
        );
    }

    // ADMIN sempre tem acesso total — sem ocultação nem mascaramento de salário.
    private ProjectResponseDTO toResponseDTO(Project p) {
        return projectResponseAssembler.toDTO(
                p, ProjectResponseAssembler.Viewer.ADMIN, toPublicCompanyDTO(p.getCompany()));
    }

    private PublicCompanyDTO toPublicCompanyDTO(Company c) {
        return new PublicCompanyDTO(
                c.getId(),
                c.getCompanyName(),
                c.getDescription(),
                c.getCity(),
                c.getUf(),
                c.getReputation(),
                c.getProfilePhotoUrl(),
                null,
                c.getTaxId(),
                c.getStatus().name(),
                List.of(),
                c.getUser() != null ? c.getUser().getEmail() : null,
                c.getType().name()
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
                c.getLinkedinUrl(),
                c.getType().name()
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
                p.getLinkedinUrl(),
                p.getResume(),
                p.getGithubUrl(),
                extractGithubLogin(p.getGithubUrl()),
                p.getGithubUrl() != null,
                professionalCredentialService.findByProfessional(p.getId())
        );
    }

    // tranforma "https://github.com/user" -> "user"
    private String extractGithubLogin(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank()) {
            return null;
        }
        return githubUrl.substring(githubUrl.lastIndexOf("/") + 1);
    }
}