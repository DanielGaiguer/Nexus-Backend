package com.main.nexus.service;

import com.main.nexus.dto.MatchConfirmationDTO;
import com.main.nexus.dto.PendingStatusCheckDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchConfirmationPendingReason;
import com.main.nexus.model.enums.MatchConfirmationResolution;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import com.main.nexus.model.enums.MatchOutcome;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.MatchConfirmationRepository;
import com.main.nexus.repository.MatchHistoryRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MatchStatusCheckRepository;
import com.main.nexus.repository.PreviousProjectRepository;
import com.main.nexus.repository.ProfessionalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Orquestra a janela de confirmacao pos-contratacao (camada financeira, Prompt 2).
//
// Fluxo: 30 dias corridos apos o fechamento (StatusMatch.MATCHED, medido pelo
// MatchHistory) um job abre a MatchConfirmation e notifica os dois lados. Cada
// lado responde em /matches/{id}/status-check: "concluiu?" (MatchOutcome) + valor
// final. Quando os dois responderam, reconcilia:
//   - ambos "nao houve trabalho"      -> CLOSED_NO_CHARGE (sem cobranca, nao conta gratuita)
//   - um sim / um nao                  -> PENDING_ADMIN_REVIEW / COMPLETION_DISAGREEMENT
//   - ambos "houve", valores <= tol.   -> CONFIRMED (media) + CommissionService.registerClosedHire
//   - ambos "houve", valores > tol.    -> PENDING_ADMIN_REVIEW / VALUE_DIVERGENCE
// Se o prazo de 7 dias estoura sem os dois responderem -> PENDING_ADMIN_REVIEW / NO_RESPONSE.
//
// A cobranca em si e o Prompt 5. Aqui CONFIRMED so deixa a contratacao pronta.
@Service
public class MatchStatusCheckService {

    private static final int WINDOW_OPENS_AFTER_DAYS = 30;
    private static final int RESPONSE_DEADLINE_DAYS = 7;

    // Tolerancia para "os valores baterem": o maior entre R$ 50,00 e 2% do maior valor.
    private static final BigDecimal MIN_TOLERANCE = new BigDecimal("50.00");
    private static final BigDecimal TOLERANCE_RATE = new BigDecimal("0.02");

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchStatusCheckRepository matchStatusCheckRepository;

    @Autowired
    private MatchConfirmationRepository matchConfirmationRepository;

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

    @Autowired
    private PreviousProjectRepository previousProjectRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private CommissionService commissionService;

    // ─── Jobs (NexusScheduler) ───────────────────────────────────────

    // Abre a janela de confirmacao para matches MATCHED cujo fechamento completou
    // 30 dias e que ainda nao tem MatchConfirmation. Idempotente: a existencia da
    // MatchConfirmation e a trava de "ja abriu".
    @Transactional
    public void openDueConfirmationWindows() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(WINDOW_OPENS_AFTER_DAYS);

        for (Match match : matchRepository.findByStatus(StatusMatch.MATCHED)) {
            if (matchConfirmationRepository.existsByMatchId(match.getId())) {
                continue;
            }
            LocalDateTime closedAt = matchHistoryRepository.findLastMatchedAt(match.getId());
            if (closedAt == null || closedAt.isAfter(cutoff)) {
                continue;
            }

            MatchConfirmation confirmation = new MatchConfirmation();
            confirmation.setMatch(match);
            confirmation.setStatus(MatchConfirmationStatus.AWAITING_RESPONSES);
            LocalDateTime now = LocalDateTime.now();
            confirmation.setOpenedAt(now);
            confirmation.setDeadline(now.plusDays(RESPONSE_DEADLINE_DAYS));
            confirmation.setSuggestedAmount(computeSuggestedAmount(match));
            confirmation.setUpdatedAt(now);
            matchConfirmationRepository.save(confirmation);

            notifyWindowOpened(match);
        }
    }

    // Move para PENDING_ADMIN_REVIEW / NO_RESPONSE as janelas ainda abertas cujo
    // prazo de 7 dias ja passou (ou seja, faltou pelo menos um lado responder --
    // se os dois tivessem respondido, reconcile ja teria fechado a janela).
    @Transactional
    public void closeOverdueConfirmationWindows() {
        List<MatchConfirmation> overdue = matchConfirmationRepository
                .findByStatusAndDeadlineBefore(
                        MatchConfirmationStatus.AWAITING_RESPONSES, LocalDateTime.now());

        for (MatchConfirmation confirmation : overdue) {
            confirmation.setStatus(MatchConfirmationStatus.PENDING_ADMIN_REVIEW);
            confirmation.setPendingReason(MatchConfirmationPendingReason.NO_RESPONSE);
            confirmation.setResolvedAt(LocalDateTime.now());
            confirmation.setUpdatedAt(LocalDateTime.now());
            matchConfirmationRepository.save(confirmation);
            notifyResolved(confirmation);
        }
    }

    // ─── Resposta de um lado ─────────────────────────────────────────

    @Transactional
    public MatchConfirmationDTO recordAnswer(
            Long matchId, UserDTO caller, MatchOutcome outcome, BigDecimal finalAmount) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + matchId));

        AuthorType side = resolveSideOrThrow(match, caller);

        MatchConfirmation confirmation = matchConfirmationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404),
                        "A janela de confirmação ainda não abriu para este match."));

        if (confirmation.getStatus() != MatchConfirmationStatus.AWAITING_RESPONSES) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Esta confirmação já foi finalizada.");
        }
        if (matchStatusCheckRepository.existsByMatchIdAndAnsweredBy(matchId, side)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Você já respondeu esta confirmação.");
        }
        if (outcome == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Informe o resultado da contratação.");
        }

        boolean workHappened = isPositive(outcome);
        BigDecimal amount = null;
        if (workHappened) {
            if (finalAmount == null || finalAmount.signum() <= 0) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Informe o valor final combinado.");
            }
            amount = finalAmount.setScale(2, RoundingMode.HALF_UP);
        }

        MatchStatusCheck answer = new MatchStatusCheck();
        answer.setMatch(match);
        answer.setAnsweredBy(side);
        answer.setOutcome(outcome);
        answer.setFinalAmount(amount);
        answer.setAnsweredAt(LocalDateTime.now());
        matchStatusCheckRepository.save(answer);

        // Portfolio do profissional: comportamento de sempre -- adiciona quando o
        // CONTRATANTE confirma que houve trabalho. Resposta do profissional nao adiciona.
        if (side == AuthorType.COMPANY && workHappened) {
            addProjectToProfessionalPortfolio(match, outcome);
        }

        reconcile(confirmation);
        return buildConfirmationDTO(match, side);
    }

    // ─── Reconciliacao ───────────────────────────────────────────────

    private void reconcile(MatchConfirmation confirmation) {
        Long matchId = confirmation.getMatch().getId();
        Optional<MatchStatusCheck> companyAnswer =
                matchStatusCheckRepository.findByMatchIdAndAnsweredBy(matchId, AuthorType.COMPANY);
        Optional<MatchStatusCheck> professionalAnswer =
                matchStatusCheckRepository.findByMatchIdAndAnsweredBy(matchId, AuthorType.PROFESSIONAL);

        if (companyAnswer.isEmpty() || professionalAnswer.isEmpty()) {
            return; // ainda falta um lado -- segue AWAITING_RESPONSES
        }

        boolean companyWork = isPositive(companyAnswer.get().getOutcome());
        boolean professionalWork = isPositive(professionalAnswer.get().getOutcome());

        LocalDateTime now = LocalDateTime.now();
        confirmation.setResolvedAt(now);
        confirmation.setUpdatedAt(now);

        if (!companyWork && !professionalWork) {
            confirmation.setStatus(MatchConfirmationStatus.CLOSED_NO_CHARGE);
            confirmation.setResolution(MatchConfirmationResolution.PARTIES_AGREED);
            matchConfirmationRepository.save(confirmation);
            notifyResolved(confirmation);
            return;
        }

        if (companyWork != professionalWork) {
            confirmation.setStatus(MatchConfirmationStatus.PENDING_ADMIN_REVIEW);
            confirmation.setPendingReason(MatchConfirmationPendingReason.COMPLETION_DISAGREEMENT);
            matchConfirmationRepository.save(confirmation);
            notifyResolved(confirmation);
            return;
        }

        // Os dois confirmaram que houve trabalho -- compara os valores.
        BigDecimal a = companyAnswer.get().getFinalAmount();
        BigDecimal b = professionalAnswer.get().getFinalAmount();
        BigDecimal diff = a.subtract(b).abs();
        BigDecimal tolerance = toleranceFor(a, b);

        if (diff.compareTo(tolerance) <= 0) {
            BigDecimal confirmed = a.add(b).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            confirmation.setStatus(MatchConfirmationStatus.CONFIRMED);
            confirmation.setConfirmedAmount(confirmed);
            confirmation.setResolution(MatchConfirmationResolution.PARTIES_AGREED);
            matchConfirmationRepository.save(confirmation);
            // Liga a camada financeira: incrementa o contador (Prompt 1) e, se
            // estiver fora das 3 gratuitas, cria/dispara a cobranca da comissao (Prompt 5).
            commissionService.onHireConfirmed(confirmation);
            notifyResolved(confirmation);
        } else {
            confirmation.setStatus(MatchConfirmationStatus.PENDING_ADMIN_REVIEW);
            confirmation.setPendingReason(MatchConfirmationPendingReason.VALUE_DIVERGENCE);
            matchConfirmationRepository.save(confirmation);
            notifyResolved(confirmation);
        }
    }

    private BigDecimal toleranceFor(BigDecimal a, BigDecimal b) {
        BigDecimal larger = a.max(b);
        BigDecimal pct = larger.multiply(TOLERANCE_RATE);
        return pct.max(MIN_TOLERANCE);
    }

    private boolean isPositive(MatchOutcome outcome) {
        return outcome == MatchOutcome.WORKING_TOGETHER
                || outcome == MatchOutcome.PROJECT_COMPLETED;
    }

    // ─── Pendencia para o indicador do dashboard ─────────────────────

    public Optional<PendingStatusCheckDTO> findPendingConfirmationFor(UserDTO caller) {
        Long companyId = null;
        Long professionalId = null;
        AuthorType side;

        if ("COMPANY".equals(caller.role())) {
            companyId = companyRepository.findByUserId(caller.id()).map(Company::getId).orElse(null);
            side = AuthorType.COMPANY;
        } else if ("PROFESSIONAL".equals(caller.role())) {
            professionalId = professionalRepository.findByUserId(caller.id())
                    .map(Professional::getId).orElse(null);
            side = AuthorType.PROFESSIONAL;
        } else {
            return Optional.empty();
        }
        if (companyId == null && professionalId == null) {
            return Optional.empty();
        }

        return matchConfirmationRepository
                .findPendingForParty(companyId, professionalId, side)
                .stream()
                .findFirst()
                .map(c -> {
                    Match m = c.getMatch();
                    String otherParty = side == AuthorType.COMPANY
                            ? m.getProfessional().getName()
                            : m.getProject().getCompany().getCompanyName();
                    return new PendingStatusCheckDTO(
                            m.getId(),
                            otherParty,
                            m.getProject().getTitle(),
                            m.getProject().getOpportunityType() != null
                                    ? m.getProject().getOpportunityType().name() : null,
                            c.getSuggestedAmount());
                });
    }

    // ─── DTO embutido no MatchResponseDTO ───────────────────────────

    // viewerSide: lado de quem esta olhando (COMPANY/PROFESSIONAL) ou null (Admin).
    public MatchConfirmationDTO buildConfirmationDTO(Match match, AuthorType viewerSide) {
        MatchConfirmation confirmation = matchConfirmationRepository
                .findByMatchId(match.getId()).orElse(null);
        if (confirmation == null) {
            return null;
        }

        List<MatchStatusCheck> checks = matchStatusCheckRepository.findByMatchId(match.getId());
        boolean companyAnswered = checks.stream()
                .anyMatch(c -> c.getAnsweredBy() == AuthorType.COMPANY);
        boolean professionalAnswered = checks.stream()
                .anyMatch(c -> c.getAnsweredBy() == AuthorType.PROFESSIONAL);

        Boolean viewerAnswered = null;
        if (viewerSide == AuthorType.COMPANY) {
            viewerAnswered = companyAnswered;
        } else if (viewerSide == AuthorType.PROFESSIONAL) {
            viewerAnswered = professionalAnswered;
        }

        return new MatchConfirmationDTO(
                confirmation.getStatus().name(),
                confirmation.getPendingReason() != null ? confirmation.getPendingReason().name() : null,
                confirmation.getOpenedAt(),
                confirmation.getDeadline(),
                confirmation.getSuggestedAmount(),
                confirmation.getConfirmedAmount(),
                companyAnswered,
                professionalAnswered,
                viewerAnswered,
                confirmation.isAdminReviewed());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private AuthorType resolveSideOrThrow(Match match, UserDTO caller) {
        if ("COMPANY".equals(caller.role())) {
            Long companyId = companyRepository.findByUserId(caller.id())
                    .map(Company::getId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(404), "Company profile not found"));
            if (!match.getProject().getCompany().getId().equals(companyId)) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "This match does not belong to your company.");
            }
            return AuthorType.COMPANY;
        }
        if ("PROFESSIONAL".equals(caller.role())) {
            Long professionalId = professionalRepository.findByUserId(caller.id())
                    .map(Professional::getId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(404), "Profile not found"));
            if (!match.getProfessional().getId().equals(professionalId)) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "This match does not belong to you.");
            }
            return AuthorType.PROFESSIONAL;
        }
        throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                "Only the parties to the match can answer the confirmation.");
    }

    // Pre-preenchimento: proposta aceita > teto do projeto/vaga. So sugestao.
    private BigDecimal computeSuggestedAmount(Match match) {
        if (match.getAcceptedProposal() != null
                && match.getAcceptedProposal().getProposedValue() != null) {
            return toMoney(match.getAcceptedProposal().getProposedValue());
        }
        Project project = match.getProject();
        if (project.getOpportunityType() == OpportunityType.JOB) {
            Double salary = project.getMonthlySalaryMax() != null
                    ? project.getMonthlySalaryMax() : project.getMonthlySalaryMin();
            return toMoney(salary);
        }
        Double budget = project.getMaximumBudget() != null
                ? project.getMaximumBudget() : project.getMinimumBudget();
        return toMoney(budget);
    }

    private BigDecimal toMoney(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private void notifyWindowOpened(Match match) {
        User companyUser = match.getProject().getCompany().getUser();
        User professionalUser = match.getProfessional().getUser();
        String companyName = match.getProject().getCompany().getCompanyName();
        String professionalName = match.getProfessional().getName();
        String projectTitle = match.getProject().getTitle();

        notificationService.notifyConfirmationWindowOpened(
                companyUser, professionalName, projectTitle, match.getId());
        notificationService.notifyConfirmationWindowOpened(
                professionalUser, companyName, projectTitle, match.getId());

        sendWindowEmail(companyUser.getEmail(), professionalName, projectTitle, match.getId());
        sendWindowEmail(professionalUser.getEmail(), companyName, projectTitle, match.getId());
    }

    private void sendWindowEmail(String to, String otherParty, String projectTitle, Long matchId) {
        String html = emailTemplateService.render(
                "Confirme sua contratação",
                List.of(
                        "Sua contratação com " + otherParty + " no projeto \"" + projectTitle
                                + "\" completou 30 dias.",
                        "Precisamos que você confirme, em poucos cliques, se o trabalho foi concluído "
                                + "e qual foi o valor final combinado entre as partes.",
                        "Você tem 7 dias para responder. Se os dois lados confirmarem e os valores "
                                + "baterem, está tudo certo. Caso contrário, o suporte do Nexus revisa o caso."),
                new EmailTemplateService.Button("Responder agora", "/matches/" + matchId + "/status-check"));
        String text = emailTemplateService.renderText(
                "Confirme sua contratação",
                List.of("Sua contratação com " + otherParty + " no projeto \"" + projectTitle
                        + "\" completou 30 dias. Confirme se o trabalho foi concluído e o valor final combinado."),
                new EmailTemplateService.Button("Responder agora", "/matches/" + matchId + "/status-check"));
        emailService.sendHtml(to, "Confirme sua contratação — Nexus", html, text);
    }

    private void notifyResolved(MatchConfirmation confirmation) {
        Match match = confirmation.getMatch();
        User companyUser = match.getProject().getCompany().getUser();
        User professionalUser = match.getProfessional().getUser();
        String projectTitle = match.getProject().getTitle();

        switch (confirmation.getStatus()) {
            case CONFIRMED -> {
                notificationService.notifyConfirmationConfirmed(companyUser, projectTitle, match.getId());
                notificationService.notifyConfirmationConfirmed(professionalUser, projectTitle, match.getId());
            }
            case CLOSED_NO_CHARGE -> {
                notificationService.notifyConfirmationClosedNoCharge(companyUser, projectTitle, match.getId());
                notificationService.notifyConfirmationClosedNoCharge(professionalUser, projectTitle, match.getId());
            }
            case PENDING_ADMIN_REVIEW -> {
                notificationService.notifyConfirmationPendingReview(companyUser, projectTitle, match.getId());
                notificationService.notifyConfirmationPendingReview(professionalUser, projectTitle, match.getId());
            }
            default -> { /* AWAITING_RESPONSES nao gera aviso de resolucao */ }
        }
    }

    // Igual ao comportamento anterior: cria um PreviousProject no portfolio do
    // profissional quando o contratante confirma que houve trabalho.
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
        notificationService.notifyProjectAddedToPortfolio(
                professional.getUser(), companyName, project.getTitle(), completed);

        String situacao = completed
                ? "que o projeto \"" + project.getTitle() + "\" foi concluído"
                : "que vocês estão trabalhando juntos no projeto \"" + project.getTitle() + "\"";
        emailService.send(
                professional.getUser().getEmail(),
                "Projeto adicionado ao seu portfólio — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                companyName + " enviou um relatório confirmando " + situacao + ".\n\n" +
                "Esse projeto foi adicionado automaticamente ao seu portfólio no Nexus.\n\nEquipe Nexus"
        );
    }
}
