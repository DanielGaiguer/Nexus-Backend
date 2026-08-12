package com.main.nexus.controller;

import com.main.nexus.dto.CompanyDirectoryItemDTO;
import com.main.nexus.dto.CompanyDirectoryPageDTO;
import com.main.nexus.dto.CompanyPreviousProjectDTO;
import com.main.nexus.dto.ProfessionalDirectoryItemDTO;
import com.main.nexus.dto.ProfessionalDirectoryPageDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.PublicProfessionalDTO;
import com.main.nexus.dto.PublicProjectDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.ReputationExplanationDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProjectResponseAssembler;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicProfessionalController {

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MatchService matchService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ProjectResponseAssembler projectResponseAssembler;

    // Endpoints em /api/public são permitAll, mas o JwtFilter ainda popula o SecurityContext
    // quando um Authorization Bearer válido é enviado. Assim conseguimos identificar se quem
    // está olhando é a própria empresa dona, outra empresa, um profissional, o admin, ou
    // um visitante anônimo (tratado como profissional para fins de visibilidade de salário).
    private ProjectResponseAssembler.Viewer resolveViewer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDTO logged)) {
            return ProjectResponseAssembler.Viewer.ANONYMOUS;
        }

        UserType type = UserType.valueOf(logged.role());
        if (type == UserType.COMPANY) {
            return companyService.findByUserId(logged.id())
                    .map(c -> ProjectResponseAssembler.Viewer.company(c.getId()))
                    .orElse(ProjectResponseAssembler.Viewer.ANONYMOUS);
        }
        if (type == UserType.ADMIN) {
            return ProjectResponseAssembler.Viewer.ADMIN;
        }
        return ProjectResponseAssembler.Viewer.professional();
    }

    @GetMapping("/professional/{id}")
    public ResponseEntity<PublicProfessionalDTO> getProfessional(@PathVariable Long id) {
        Optional<Professional> optional = professionalRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Professional p = optional.get();

        List<String> skillNames = p.getSkills() != null
                ? p.getSkills().stream().map(s -> s.getName()).toList()
                : List.of();

        List<PublicProjectDTO> previousProjects = p.getProjects() != null
                ? p.getProjects().stream()
                        .map(proj -> new PublicProjectDTO(
                                proj.getTitle(),
                                proj.getTechnologies(),
                                proj.getYearOfCompletion()
                        ))
                        .toList()
                : List.of();

        ReputationMetrics metrics = reputationMetricsRepository.findByProfessionalId(id).orElse(null);
        Double overallScore = metrics != null ? metrics.getReputationScore() : null;

        List<String> preferredTypeNames = p.getPreferredTypes() != null
                ? p.getPreferredTypes().stream().map(Enum::name).toList()
                : List.of();

        PublicProfessionalDTO dto = new PublicProfessionalDTO(
                p.getId(),
                p.getName(),
                p.getCity(),
                p.getUf(),
                p.getExperienceLevel() != null ? p.getExperienceLevel().name() : null,
                p.getReputation(),
                p.getAvailable(),
                skillNames,
                previousProjects,
                overallScore,
                metrics != null ? toReputationDTO(metrics) : null,
                p.getProfilePhotoUrl(),
                p.getFreelanceMinExpectation(),
                p.getFreelanceMaxExpectation(),
                preferredTypeNames
        );

        return ResponseEntity.ok(dto);
    }

    // Diretório de empresas paginado, com busca por nome, disponível para qualquer role logada
    @GetMapping("/companies")
    public ResponseEntity<CompanyDirectoryPageDTO> listCompanies(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        // Pageable representa as informações de paginação
        // Sort.by("companyName").ascending(), Define a ordenação pelo campo companyName em ordem crescente.
        Pageable pageable = PageRequest.of(page, size, Sort.by("companyName").ascending());
        Page<Company> result = companyRepository.findByStatusAndCompanyNameContainingIgnoreCase(
                CompanyStatus.APPROVED, search != null ? search : "", pageable);

        List<CompanyDirectoryItemDTO> content = result.getContent().stream()
                .map(c -> new CompanyDirectoryItemDTO(
                        c.getId(),
                        c.getCompanyName(),
                        c.getCity(),
                        c.getUf(),
                        c.getReputation(),
                        c.getProfilePhotoUrl(),
                        c.getDescription()
                ))
                .toList();

        return ResponseEntity.ok(new CompanyDirectoryPageDTO(content, result.hasNext()));
    }

    // Diretório de profissionais — paginado, com busca por nome, disponível para qualquer role logada
    @GetMapping("/professionals")
    public ResponseEntity<ProfessionalDirectoryPageDTO> listProfessionals(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Professional> result = professionalRepository.findByNameContainingIgnoreCase(
                search != null ? search : "", pageable);

        List<ProfessionalDirectoryItemDTO> content = result.getContent().stream()
                .map(p -> new ProfessionalDirectoryItemDTO(
                        p.getId(),
                        p.getName(),
                        p.getCity(),
                        p.getUf(),
                        p.getReputation(),
                        p.getProfilePhotoUrl(),
                        p.getExperienceLevel() != null ? p.getExperienceLevel().name() : null,
                        p.getSkills() != null ? p.getSkills().stream().map(s -> s.getName()).toList() : List.of()
                ))
                .toList();

        return ResponseEntity.ok(new ProfessionalDirectoryPageDTO(content, result.hasNext()));
    }

    private ReputationExplanationDTO toReputationDTO(ReputationMetrics m) {
        return new ReputationExplanationDTO(
                m.getReputationScore(),
                m.getConfidenceScore() != null ? m.getConfidenceScore() * 100 : 0.0,
                m.getTotalReviews(),
                m.getTechnicalCompetence(),
                m.getCommunication(),
                m.getReliability(),
                m.getPunctuality(),
                m.getProfessionalism(),
                m.getSatisfactionAverage(),
                m.getRecommendationRate()
        );
    }

    @GetMapping("/opportunity/{id}")
    public ResponseEntity<ProjectResponseDTO> getOpportunity(@PathVariable Long id) {
        Optional<Project> optional = projectRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project p = optional.get();
        ProjectResponseAssembler.Viewer viewer = resolveViewer();

        // Fora do OPEN (pausado ou encerrado), só quem tem relação direta com o projeto
        // continua enxergando o detalhe: a própria empresa dona, o admin, ou um profissional
        // que já tem algum match com ele, pra conseguir acompanhar o que aconteceu com a
        // oportunidade (aguardando aprovação, confirmada, recusada ou cancelada).
        if (p.getStatus() != ProjectStatus.OPEN && !canViewNonOpenProject(p, viewer)) {
            return ResponseEntity.notFound().build();
        }

        Company c = p.getCompany();
        PublicCompanyDTO companyDTO = new PublicCompanyDTO(
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
                c.getUser() != null ? c.getUser().getEmail() : null
        );

        // Se outra empresa acessar diretamente uma oportunidade marcada como não visível
        // para empresas, tratamos como não encontrada, o link direto não pode burlar a regra.
        return projectResponseAssembler.toVisibleDTO(p, viewer, companyDTO)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // por padrão, uma oportunidade fora de OPEN (ou seja, PAUSED ou CLOSED) fica invisível ao público geral — mas existem três exceções, cada uma checada por tipo de "espectador"
    // - ADMIN: sempre pode ver, independente do status.
    // - A própria empresa dona: pode ver seus próprios projetos mesmo pausados/encerrados (viewer.companyId().equals(project.getCompany().getId())).
    // - Um profissional com match no projeto: se o profissional já tem qualquer registro de Match para aquele projeto 
    private boolean canViewNonOpenProject(Project project, ProjectResponseAssembler.Viewer viewer) {
        if (viewer.type() == UserType.ADMIN) {
            return true;
        }
        if (viewer.type() == UserType.COMPANY) {
            return viewer.companyId() != null && viewer.companyId().equals(project.getCompany().getId());
        }
        if (viewer.type() == UserType.PROFESSIONAL) {
            Long professionalId = resolveLoggedProfessionalId();
            return professionalId != null
                    && matchRepository.findByProjectIdAndProfessionalId(project.getId(), professionalId).isPresent();
        }
        return false;
    }

    private Long resolveLoggedProfessionalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDTO logged)) {
            return null;
        }
        if (!UserType.PROFESSIONAL.name().equals(logged.role())) {
            return null;
        }
        return professionalRepository.findByUserId(logged.id()).map(Professional::getId).orElse(null);
    }

    @GetMapping("/company/{id}")
    public ResponseEntity<PublicCompanyDTO> getCompany(@PathVariable Long id) {
        Optional<Company> optional = companyRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Company c = optional.get();
        if (c.getStatus() != CompanyStatus.APPROVED) {
            return ResponseEntity.notFound().build();
        }

        ReputationMetrics metrics = reputationMetricsRepository.findByCompanyId(id).orElse(null);

        List<CompanyPreviousProjectDTO> previousProjects = matchService.getPreviousProjectsByCompany(id)
                .stream()
                .map(m -> new CompanyPreviousProjectDTO(m.getId(), m.getProject().getTitle(), m.getCreatedAt()))
                .toList();

        PublicCompanyDTO dto = new PublicCompanyDTO(
                c.getId(),
                c.getCompanyName(),
                c.getDescription(),
                c.getCity(),
                c.getUf(),
                c.getReputation(),
                c.getProfilePhotoUrl(),
                metrics != null ? toReputationDTO(metrics) : null,
                c.getTaxId(),
                c.getStatus().name(),
                previousProjects,
                c.getUser() != null ? c.getUser().getEmail() : null
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/company/{id}/projects")
    public ResponseEntity<List<ProjectResponseDTO>> getCompanyOpenProjects(@PathVariable Long id) {
        Optional<Company> optional = companyRepository.findById(id);
        if (optional.isEmpty() || optional.get().getStatus() != CompanyStatus.APPROVED) {
            return ResponseEntity.notFound().build();
        }

        ProjectResponseAssembler.Viewer viewer = resolveViewer();
        List<ProjectResponseDTO> openProjects = projectRepository.findByCompanyId(id).stream()
                .filter(project -> project.getStatus() == ProjectStatus.OPEN)
                .map(project -> projectResponseAssembler.toVisibleDTO(project, viewer, null))
                .flatMap(Optional::stream)
                .toList();

        return ResponseEntity.ok(openProjects);
    }

    // Histórico de oportunidades encerradas (vagas e projetos) — visível publicamente
    // para admin, profissionais e demais empresas no perfil da empresa (respeitando a
    // mesma regra de "não visível para outras empresas")
    @GetMapping("/company/{id}/projects/closed")
    public ResponseEntity<List<ProjectResponseDTO>> getCompanyClosedProjects(@PathVariable Long id) {
        Optional<Company> optional = companyRepository.findById(id);
        if (optional.isEmpty() || optional.get().getStatus() != CompanyStatus.APPROVED) {
            return ResponseEntity.notFound().build();
        }

        ProjectResponseAssembler.Viewer viewer = resolveViewer();
        List<ProjectResponseDTO> closedProjects = projectRepository.findByCompanyId(id).stream()
                .filter(project -> project.getStatus() == ProjectStatus.CLOSED)
                .map(project -> projectResponseAssembler.toVisibleDTO(project, viewer, null))
                .flatMap(Optional::stream)
                .toList();

        return ResponseEntity.ok(closedProjects);
    }
}
