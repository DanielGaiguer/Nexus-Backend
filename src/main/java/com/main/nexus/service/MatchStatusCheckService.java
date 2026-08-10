package com.main.nexus.service;

import com.main.nexus.dto.PendingStatusCheckDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchOutcome;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MatchStatusCheckRepository;
import com.main.nexus.repository.PreviousProjectRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchStatusCheckService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchStatusCheckRepository matchStatusCheckRepository;

    @Autowired
    private PreviousProjectRepository previousProjectRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Transactional
    public MatchStatusCheck answerStatusCheck(Long matchId, Long companyId, MatchOutcome outcome) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + matchId));

        if (!match.getProject().getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This match does not belong to your company.");
        }

        if (match.getStatus() != StatusMatch.MATCHED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not a confirmed match.");
        }

        if (matchStatusCheckRepository.existsByMatchId(matchId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This match has already been answered.");
        }

        MatchStatusCheck statusCheck = new MatchStatusCheck();
        statusCheck.setMatch(match);
        statusCheck.setAnsweredBy(AuthorType.COMPANY);
        statusCheck.setOutcome(outcome);
        MatchStatusCheck saved = matchStatusCheckRepository.save(statusCheck);

        if (outcome == MatchOutcome.WORKING_TOGETHER || outcome == MatchOutcome.PROJECT_COMPLETED) {
            addProjectToProfessionalPortfolio(match, outcome);
        }

        return saved;
    }

    // Match mais antigo, já com 14+ dias, ainda sem resposta da empresa — usado pra
    // abrir a pergunta automaticamente assim que a empresa entra no dashboard, sem
    // depender dela ir abrir a notificação manualmente.
    public Optional<PendingStatusCheckDTO> findPendingForCompany(Long companyId) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(MatchExpirationService.APPROACHING_THRESHOLD_DAYS);

        return matchRepository.findPendingStatusChecksByCompanyId(companyId, threshold)
                .stream()
                .findFirst()
                .map(m -> new PendingStatusCheckDTO(
                        m.getId(),
                        m.getProfessional().getName(),
                        m.getProject().getTitle()));
    }

    // Chamado tanto para WORKING_TOGETHER (projeto em andamento) quanto para
    // PROJECT_COMPLETED (projeto concluído) — nos dois casos o projeto entra no
    // portfólio do profissional, só o texto do aviso muda.
    private void addProjectToProfessionalPortfolio(Match match, MatchOutcome outcome) {
        Project project = match.getProject();
        Professional professional = match.getProfessional();
        String companyName = project.getCompany().getCompanyName();

        PreviousProject previousProject = new PreviousProject();
        previousProject.setProfessional(professional);
        previousProject.setTitle(project.getTitle());
        previousProject.setDescription(project.getDescription());
        previousProject.setTechnologies(project.getRequiredSkills().stream()
                .map(Skill::getName)
                .toList());
        previousProject.setYearOfCompletion(LocalDate.now().getYear());

        previousProjectRepository.save(previousProject);

        boolean completed = outcome == MatchOutcome.PROJECT_COMPLETED;
        String situacao = completed
                ? "que o projeto \"" + project.getTitle() + "\" foi concluído"
                : "que vocês estão trabalhando juntos no projeto \"" + project.getTitle() + "\"";

        // Avisa o profissional — por notificação in-app e e-mail — que a empresa
        // confirmou o andamento do projeto e ele já entrou no portfólio automaticamente.
        notificationService.notifyProjectAddedToPortfolio(
                professional.getUser(), companyName, project.getTitle(), completed);

        emailService.send(
                professional.getUser().getEmail(),
                "Projeto adicionado ao seu portfólio — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                companyName + " enviou um relatório confirmando " + situacao + ".\n\n" +
                "Esse projeto foi adicionado automaticamente ao seu portfólio no Nexus.\n\nEquipe Nexus"
        );
    }
}
