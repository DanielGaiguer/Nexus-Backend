package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.repository.ProjectRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MatchService matchService;
    
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    public Project save(Project project) {
        validateByType(project);
        Project saved = projectRepository.save(project);
        matchService.generateRankingForProject(saved);
        return saved;
    }

    public Project update(Project project) {
        validateByType(project);
        Project saved = projectRepository.save(project);
        matchService.recalculateRankingForProject(saved);
        return saved;
    }

    private void validateByType(Project project) {
        if (project.getOpportunityType() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "opportunityType is required. Use PROJECT or JOB.");
        }

        if (project.getOpportunityType() == OpportunityType.PROJECT) {
            // Campos de JOB não devem estar preenchidos
            if (project.getContractType() != null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Field 'contractType' is not allowed for a PROJECT.");
            }
            if (project.getMonthlySalaryMin() != null || project.getMonthlySalaryMax() != null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Fields 'monthlySalaryMin' and 'monthlySalaryMax' are not allowed for a PROJECT.");
            }
            if (project.getBenefits() != null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Field 'benefits' is not allowed for a PROJECT.");
            }
            if (project.getWorkloadHoursPerWeek() != null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Field 'workloadHoursPerWeek' is not allowed for a PROJECT.");
            }
            // Campos obrigatórios para PROJECT
            if (project.getMinimumBudget() == null || project.getMaximumBudget() == null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Fields 'minimumBudget' and 'maximumBudget' are required for a PROJECT.");
            }
            if (project.getDeadline() == null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Field 'deadline' is required for a PROJECT.");
            }
        }

        if (project.getOpportunityType() == OpportunityType.JOB) {
            // Campo exclusivo de PROJECT
            if (Boolean.TRUE.equals(project.getAcceptsProposals())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Field 'acceptsProposals' is not allowed for a JOB.");
            }
            // Campos de PROJECT não devem estar preenchidos
            if (project.getMinimumBudget() != null || project.getMaximumBudget() != null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Fields 'minimumBudget' and 'maximumBudget' are not allowed for a JOB.");
            }
            if (project.getDeadline() != null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Field 'deadline' is not allowed for a JOB. Use 'startDate' instead.");
            }
            // Campos obrigatórios para JOB
            if (project.getContractType() == null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Field 'contractType' is required for a JOB.");
            }
            if (project.getMonthlySalaryMin() == null || project.getMonthlySalaryMax() == null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Fields 'monthlySalaryMin' and 'monthlySalaryMax' are required for a JOB.");
            }
        }
    }
    
    public Project findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + id));
    }

    // busca validando que o projeto pertence à empresa 
    public Project findByIdAndCompany(Long id, Long companyId) {
        Project project = findById(id);
        validateOwnership(project, companyId);
        return project;
    }

    public List<Project> findByCompany(Company company) {
        return projectRepository.findByCompanyId(company.getId());
    }

    public List<Project> findAllOpen() {
        return projectRepository.findByStatus(ProjectStatus.OPEN);
    }

    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    public void closeProject(Long id, Long companyId) {
        Project project = findByIdAndCompany(id, companyId);
        closeProjectInternal(project);
    }

    // Encerramento por um administrador, sem exigir vínculo com a empresa dona do projeto
    public void closeProjectAsAdmin(Long id) {
        Project project = findById(id);
        closeProjectInternal(project);

        User companyUser = project.getCompany().getUser();
        notificationService.notifyProjectClosedByAdmin(companyUser, project.getTitle());
        emailService.send(
            companyUser.getEmail(),
            "Oportunidade encerrada — Nexus",
            "Olá,\n\nA sua oportunidade \"" + project.getTitle() + "\" foi encerrada por um Administrador. " +
            "Para mais informações, entre em contato com admin@gmail.com.\n\nEquipe Nexus"
        );
    }

    private void closeProjectInternal(Project project) {
        project.setStatus(ProjectStatus.CLOSED);
        projectRepository.save(project);

        // cancela e notifica matches que ainda estavam pendentes (nunca confirmados)
        matchService.cancelPendingMatchesForClosedProject(project);
    }

    // Reativa um projeto encerrado opcionalmente ajustando o nº de posições,
    // já que a vaga costuma ter sido encerrada por estar totalmente preenchida 
    public Project reopenProject(Long id, Long companyId, Integer newMaxPositions) {
        Project project = findByIdAndCompany(id, companyId);

        if (project.getStatus() != ProjectStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only closed projects can be reactivated.");
        }

        if (newMaxPositions != null) {
            if (newMaxPositions < project.getFilledPositions()) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Maximum positions cannot be less than filled positions.");
            }
            project.setMaxPositions(newMaxPositions);
        }

        project.setStatus(ProjectStatus.OPEN);
        Project saved = projectRepository.save(project);

        // Se a empresa reabriu sem aumentar o limite (ex.: manteve o valor default do modal,
        // que é o maxPositions atual) e o projeto já estava cheio, ele não pode ficar "OPEN"
        // com 0 vagas reais — pausa de novo e avisa a empresa, igual ao caso automático.
        matchService.pauseIfPositionsFull(saved);
        if (saved.getStatus() == ProjectStatus.OPEN) {
            matchService.recalculateRankingForProject(saved);
        }
        return saved;
    }

    // Resolve a pausa automática por limite de vagas: soma vagas extras ao limite atual
    // e volta o projeto a ficar OPEN (voltando a gerar ranking com novos profissionais).
    public Project resumePausedProject(Long id, Long companyId, Integer additionalPositions) {
        Project project = findByIdAndCompany(id, companyId);

        if (project.getStatus() != ProjectStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only paused projects can be resumed this way.");
        }

        if (additionalPositions == null || additionalPositions < 1) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "additionalPositions must be at least 1.");
        }

        project.setMaxPositions(project.getMaxPositions() + additionalPositions);
        project.setStatus(ProjectStatus.OPEN);
        Project saved = projectRepository.save(project);
        matchService.recalculateRankingForProject(saved);
        return saved;
    }

    public void delete(Long id, Long companyId) {
        Project project = findByIdAndCompany(id, companyId);
        projectRepository.delete(project);
    }

    // Validação de posse 
    private void validateOwnership(Project project, Long companyId) {
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }
    }
}