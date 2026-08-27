package com.main.nexus.service;

import com.main.nexus.dto.ScreeningAnswerDetailDTO;
import com.main.nexus.dto.ScreeningAnswerSubmitDTO;
import com.main.nexus.dto.ScreeningAttemptDTO;
import com.main.nexus.dto.ScreeningAttemptQuestionDTO;
import com.main.nexus.dto.ScreeningInvitationDetailDTO;
import com.main.nexus.dto.ScreeningInvitationSummaryDTO;
import com.main.nexus.dto.ScreeningProcessSummaryDTO;
import com.main.nexus.dto.ScreeningStageStatusDTO;
import com.main.nexus.dto.ScreeningSubmissionRequestDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Proposal;
import com.main.nexus.model.ScreeningAnswer;
import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.model.enums.PendingIntentType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.repository.ScreeningInvitationRepository;
import com.main.nexus.repository.ScreeningQuestionnaireRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Ciclo de vida de uma tentativa numa etapa do processo seletivo -- gate de entrada (checkGate,
// chamado por MatchService/ProposalService), fluxo de resposta (start/submit) e decisão manual da
// empresa (approveStage/reproveStage) por etapa. A navegação entre etapas (qual é a "atual" de um
// profissional, quando abrir a próxima) é toda interna a este service -- ver findCurrentStage.
// Ver PendingIntentType para como a ação que ficou pendente (interesse/aceite) é retomada quando
// a última etapa é aprovada.
@Service
public class ScreeningInvitationService {

    // Usados pelo gate (reaproveitar x abrir nova) e pelo job de expiração -- só quem ainda não
    // recebeu nenhuma resposta do profissional.
    private static final List<ScreeningInvitationStatus> PENDING_STATUSES =
            List.of(ScreeningInvitationStatus.SENT, ScreeningInvitationStatus.IN_PROGRESS);

    // Usado pelo cancelamento em cascata -- inclui SUBMITTED, porque uma etapa respondida mas
    // ainda sem decisão da empresa fica órfã pra sempre se o match/projeto em volta morrer antes
    // de ela decidir (diferente do job de expiração, que só se importa com quem nunca respondeu).
    private static final List<ScreeningInvitationStatus> CANCELLABLE_STATUSES =
            List.of(ScreeningInvitationStatus.SENT, ScreeningInvitationStatus.IN_PROGRESS,
                    ScreeningInvitationStatus.SUBMITTED);

    @Autowired
    private ScreeningInvitationRepository screeningInvitationRepository;

    @Autowired
    private ScreeningQuestionnaireRepository screeningQuestionnaireRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    // GATE — chamado por MatchService.professionalShowsInterest/professionalAccepts e por
    // ProposalService.submitProposal antes de aplicar a ação de verdade.

    public record GateResult(boolean blocked, Long invitationId) {
        public static GateResult passed() {
            return new GateResult(false, null);
        }
        public static GateResult blocked(Long invitationId) {
            return new GateResult(true, invitationId);
        }
    }

    @Transactional
    public GateResult checkGate(Project project, Professional professional,
            PendingIntentType intentType, Match pendingMatch, Proposal pendingProposal) {

        Optional<ScreeningQuestionnaire> questionnaireOpt =
                screeningQuestionnaireRepository.findByProjectId(project.getId());
        if (questionnaireOpt.isEmpty()) {
            return GateResult.passed();
        }
        ScreeningQuestionnaire questionnaire = questionnaireOpt.get();

        ScreeningStage currentStage = findCurrentStage(questionnaire, professional);
        if (currentStage == null) {
            // Aprovado em todas as etapas ativas.
            return GateResult.passed();
        }

        ScreeningInvitation latest = screeningInvitationRepository
                .findFirstByScreeningStageIdAndProfessionalIdOrderBySentAtDesc(
                        currentStage.getId(), professional.getId())
                .orElse(null);

        // Prazo estourado sem resposta é definitivo -- alinhado ao padrão real de plataformas de
        // recrutamento (etapa vencida não pode ser refeita), diferente de DECLINED/CANCELLED, que
        // continuam permitindo nova tentativa. Não abre uma tentativa nova, não mexe no
        // Match/Proposal que ficou pendente -- quem decide o que fazer com ele é a empresa, com
        // as ferramentas que já existem. REPROVED é o mesmo espírito: reprovar já fecha o
        // match/processo (ver reproveStage), então isso nem deveria ser alcançável de novo, mas
        // fica bloqueado em vez de reabrir, por segurança.
        if (latest != null && (latest.getStatus() == ScreeningInvitationStatus.EXPIRED
                || latest.getStatus() == ScreeningInvitationStatus.REPROVED)) {
            return GateResult.blocked(latest.getId());
        }

        ScreeningInvitation invitation;
        if (latest != null && (PENDING_STATUSES.contains(latest.getStatus())
                || latest.getStatus() == ScreeningInvitationStatus.SUBMITTED)) {
            // Já em andamento, ou já respondida e aguardando decisão da empresa -- reaproveita,
            // não duplica.
            invitation = latest;
        } else {
            // Não existe ainda, ou a tentativa anterior terminou em DECLINED/CANCELLED -- abre
            // uma tentativa nova nesta etapa (RN confirmada: recusar ou ter o convite cancelado
            // permite tentar de novo; só expirar/reprovar é que não permite, ver acima).
            invitation = createInvitationForStage(currentStage, professional, project);
        }

        // (Re)grava a intenção pendente -- mesmo reaproveitando uma tentativa já em andamento,
        // a intenção pode ter mudado desde a última vez (ex.: tentou aceitar um convite,
        // desistiu, agora está tentando demonstrar interesse de novo no mesmo projeto).
        invitation.setPendingIntentType(intentType);
        invitation.setPendingMatch(pendingMatch);
        invitation.setPendingProposal(pendingProposal);
        screeningInvitationRepository.save(invitation);

        return GateResult.blocked(invitation.getId());
    }

    // A primeira etapa pra qual este profissional ainda não tem uma tentativa APPROVED -- null
    // se já passou por todas. Etapas desativadas (`active=false`) são puladas, exceto quando este
    // profissional específico já tinha alguma tentativa registrada nelas antes de serem
    // removidas -- aí continuam "atuais" pra ele, sem efeito retroativo de quem já estava lá (RN
    // confirmada com o usuário).
    private ScreeningStage findCurrentStage(ScreeningQuestionnaire questionnaire, Professional professional) {
        for (ScreeningStage stage : questionnaire.getStages()) {
            boolean approved = screeningInvitationRepository.existsByScreeningStageIdAndProfessionalIdAndStatus(
                    stage.getId(), professional.getId(), ScreeningInvitationStatus.APPROVED);
            if (approved) {
                continue;
            }
            if (Boolean.TRUE.equals(stage.getActive())) {
                return stage;
            }
            boolean hadAttemptBeforeRemoval = screeningInvitationRepository
                    .existsByScreeningStageIdAndProfessionalId(stage.getId(), professional.getId());
            if (hadAttemptBeforeRemoval) {
                return stage;
            }
        }
        return null;
    }

    // A próxima etapa ativa depois de `current`, na ordem -- null se `current` era a última.
    private ScreeningStage findNextActiveStage(ScreeningQuestionnaire questionnaire, ScreeningStage current) {
        boolean passedCurrent = false;
        for (ScreeningStage stage : questionnaire.getStages()) {
            if (passedCurrent && Boolean.TRUE.equals(stage.getActive())) {
                return stage;
            }
            if (stage.getId().equals(current.getId())) {
                passedCurrent = true;
            }
        }
        return null;
    }

    private ScreeningInvitation createInvitationForStage(
            ScreeningStage stage, Professional professional, Project project) {
        ScreeningInvitation invitation = new ScreeningInvitation();
        invitation.setScreeningStage(stage);
        invitation.setProfessional(professional);
        invitation.setStatus(ScreeningInvitationStatus.SENT);
        invitation.setSentAt(LocalDateTime.now());
        invitation.setDeadlineAt(LocalDateTime.now().plusDays(stage.getResponseDeadlineDays()));
        invitation = screeningInvitationRepository.save(invitation);

        notificationService.notifyScreeningInvitationReceived(
                professional.getUser(), project.getTitle(), stage.getTitle(), invitation.getId());
        emailService.send(
                professional.getUser().getEmail(),
                "Responda para continuar — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                "Para seguir com o projeto \"" + project.getTitle() + "\", responda a etapa \"" +
                stage.getTitle() + "\" do processo seletivo. Você tem " + stage.getResponseDeadlineDays() +
                " dia(s).\n\nAcesse o Nexus para responder.\n\nEquipe Nexus"
        );
        return invitation;
    }

    // RESPOSTA — profissional

    @Transactional
    public ScreeningInvitation startAttempt(Long invitationId, Long professionalId) {
        ScreeningInvitation invitation = findById(invitationId);
        validateProfessionalOwnership(invitation, professionalId);

        if (invitation.getStatus() == ScreeningInvitationStatus.IN_PROGRESS) {
            return invitation;
        }
        if (invitation.getStatus() != ScreeningInvitationStatus.SENT) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This screening is not available to start.");
        }

        invitation.setStatus(ScreeningInvitationStatus.IN_PROGRESS);
        invitation.setStartedAt(LocalDateTime.now());
        return screeningInvitationRepository.save(invitation);
    }

    @Transactional
    public ScreeningInvitation decline(Long invitationId, Long professionalId) {
        ScreeningInvitation invitation = findById(invitationId);
        validateProfessionalOwnership(invitation, professionalId);
        assertPending(invitation);

        invitation.setStatus(ScreeningInvitationStatus.DECLINED);
        ScreeningInvitation saved = screeningInvitationRepository.save(invitation);

        Project project = invitation.getScreeningStage().getScreeningQuestionnaire().getProject();
        notificationService.notifyScreeningDeclined(
                project.getCompany().getUser(), invitation.getProfessional().getName(),
                project.getTitle(), invitation.getScreeningStage().getTitle());

        return saved;
    }

    @Transactional
    public ScreeningInvitation submit(Long invitationId, Long professionalId, ScreeningSubmissionRequestDTO request) {
        ScreeningInvitation invitation = findById(invitationId);
        validateProfessionalOwnership(invitation, professionalId);
        assertPending(invitation);

        if (LocalDateTime.now().isAfter(invitation.getDeadlineAt())) {
            invitation.setStatus(ScreeningInvitationStatus.EXPIRED);
            screeningInvitationRepository.save(invitation);
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "The deadline to respond to this screening has passed.");
        }

        ScreeningStage stage = invitation.getScreeningStage();
        List<ScreeningQuestion> questions = stage.getQuestions().stream()
                .filter(ScreeningQuestion::getActive)
                .toList();
        Map<Long, ScreeningAnswerSubmitDTO> submitted = new HashMap<>();
        if (request.answers() != null) {
            for (ScreeningAnswerSubmitDTO a : request.answers()) {
                submitted.put(a.questionId(), a);
            }
        }

        int correctCount = 0;
        int totalMultipleChoiceCount = 0;

        for (ScreeningQuestion question : questions) {
            ScreeningAnswerSubmitDTO submittedAnswer = submitted.get(question.getId());
            if (submittedAnswer == null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Missing answer for question " + question.getId() + ".");
            }

            ScreeningAnswer answer = new ScreeningAnswer();
            answer.setScreeningInvitation(invitation);
            answer.setScreeningQuestion(question);
            answer.setTimeSpentSeconds(submittedAnswer.timeSpentSeconds());

            if (question.getType() == ScreeningQuestionType.MULTIPLE_CHOICE) {
                answer.setSelectedOptionIndex(submittedAnswer.selectedOptionIndex());
                boolean correct = submittedAnswer.selectedOptionIndex() != null
                        && submittedAnswer.selectedOptionIndex().equals(question.getCorrectOptionIndex());
                answer.setCorrect(correct);
                totalMultipleChoiceCount++;
                if (correct) {
                    correctCount++;
                }
            } else {
                answer.setEssayText(submittedAnswer.essayText());
            }

            invitation.getAnswers().add(answer);
        }

        Double autoScorePercent = totalMultipleChoiceCount > 0
                ? (correctCount / (double) totalMultipleChoiceCount) * 100.0
                : null;

        invitation.setAutoScorePercent(autoScorePercent);
        invitation.setTotalTimeSpentSeconds(request.totalTimeSpentSeconds());
        invitation.setTabSwitchCount(request.tabSwitchCount());
        invitation.setSubmittedAt(LocalDateTime.now());
        // A decisão de avançar é sempre manual agora -- nunca fecha sozinho, mesmo sem
        // dissertativa (diferente do modelo de rodada única anterior).
        invitation.setStatus(ScreeningInvitationStatus.SUBMITTED);

        ScreeningInvitation saved = screeningInvitationRepository.save(invitation);

        Project project = stage.getScreeningQuestionnaire().getProject();
        notificationService.notifyScreeningSubmitted(
                project.getCompany().getUser(), saved.getProfessional().getName(),
                project.getTitle(), stage.getTitle(), saved.getId());
        emailService.send(
                project.getCompany().getUser().getEmail(),
                "Etapa do processo seletivo respondida — Nexus",
                "Olá " + project.getCompany().getCompanyName() + ",\n\n" +
                saved.getProfessional().getName() + " respondeu a etapa \"" + stage.getTitle() +
                "\" do projeto \"" + project.getTitle() + "\".\n\n" +
                "Acesse o Nexus para aprovar ou reprovar o avanço.\n\nEquipe Nexus"
        );

        return saved;
    }

    // DECISÃO — empresa

    // Sinal devolvido pro controller decidir se retoma a ação pendente (última etapa aprovada,
    // interesse/aceite) -- proposta nunca é retomada automaticamente (ver
    // ProposalService/decisão confirmada com o usuário).
    public record StageDecision(ScreeningInvitation invitation, boolean wasLastStage) {}

    @Transactional
    public StageDecision approveStage(Long invitationId, Long companyId, String comment) {
        ScreeningInvitation invitation = findById(invitationId);
        validateCompanyOwnership(invitation, companyId);

        if (invitation.getStatus() != ScreeningInvitationStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only a submitted stage pending a decision can be approved.");
        }

        invitation.setStatus(ScreeningInvitationStatus.APPROVED);
        invitation.setDecidedAt(LocalDateTime.now());
        invitation.setCompanyDecisionComment(comment);
        ScreeningInvitation saved = screeningInvitationRepository.save(invitation);

        ScreeningStage currentStage = saved.getScreeningStage();
        ScreeningQuestionnaire questionnaire = currentStage.getScreeningQuestionnaire();
        ScreeningStage nextStage = findNextActiveStage(questionnaire, currentStage);

        if (nextStage == null) {
            return new StageDecision(saved, true);
        }

        ScreeningInvitation nextInvitation = new ScreeningInvitation();
        nextInvitation.setScreeningStage(nextStage);
        nextInvitation.setProfessional(saved.getProfessional());
        nextInvitation.setStatus(ScreeningInvitationStatus.SENT);
        nextInvitation.setSentAt(LocalDateTime.now());
        nextInvitation.setDeadlineAt(LocalDateTime.now().plusDays(nextStage.getResponseDeadlineDays()));
        // Carrega a intenção pendente pra próxima etapa -- é ela que vai ser consultada quando
        // essa nova (agora última, ou não) etapa também for aprovada.
        nextInvitation.setPendingIntentType(saved.getPendingIntentType());
        nextInvitation.setPendingMatch(saved.getPendingMatch());
        nextInvitation.setPendingProposal(saved.getPendingProposal());
        nextInvitation = screeningInvitationRepository.save(nextInvitation);

        Project project = questionnaire.getProject();
        notificationService.notifyScreeningStageApproved(
                saved.getProfessional().getUser(), project.getTitle(), currentStage.getTitle(), nextInvitation.getId());
        emailService.send(
                saved.getProfessional().getUser().getEmail(),
                "Você avançou de etapa — Nexus",
                "Olá " + saved.getProfessional().getName() + ",\n\n" +
                "Você foi aprovado na etapa \"" + currentStage.getTitle() + "\" do processo seletivo do projeto \"" +
                project.getTitle() + "\". Responda a etapa \"" + nextStage.getTitle() +
                "\" para continuar.\n\nAcesse o Nexus para responder.\n\nEquipe Nexus"
        );

        return new StageDecision(saved, false);
    }

    @Transactional
    public ScreeningInvitation reproveStage(Long invitationId, Long companyId, String comment) {
        ScreeningInvitation invitation = findById(invitationId);
        validateCompanyOwnership(invitation, companyId);

        if (invitation.getStatus() != ScreeningInvitationStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only a submitted stage pending a decision can be reproved.");
        }

        invitation.setStatus(ScreeningInvitationStatus.REPROVED);
        invitation.setDecidedAt(LocalDateTime.now());
        invitation.setCompanyDecisionComment(comment);
        ScreeningInvitation saved = screeningInvitationRepository.save(invitation);

        Project project = saved.getScreeningStage().getScreeningQuestionnaire().getProject();
        notificationService.notifyScreeningStageReproved(
                saved.getProfessional().getUser(), project.getTitle(),
                saved.getScreeningStage().getTitle(), saved.getId());

        return saved;
    }

    // Chamado pelo controller quando a ÚLTIMA etapa de um processo com PROPOSAL_SUBMIT pendente é
    // aprovada -- a proposta nunca é aceita/recusada automaticamente pelo resultado da triagem
    // (decisão confirmada com o usuário), mas sem isso o profissional nunca ficaria sabendo que
    // terminou de responder tudo (notifyScreeningStageApproved só dispara quando existe próxima
    // etapa). Espelha o aviso equivalente em MatchService.applyProfessionalInterest pro caso de
    // match ainda aguardando aceite da empresa.
    public void notifyProposalScreeningCompleted(ScreeningInvitation invitation) {
        Professional professional = invitation.getProfessional();
        Project project = invitation.getScreeningStage().getScreeningQuestionnaire().getProject();

        notificationService.notifyScreeningApprovedAwaitingProposalDecision(
                professional.getUser(), project.getTitle());
        emailService.send(
                professional.getUser().getEmail(),
                "Processo seletivo concluído — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                "Você foi aprovado em todas as etapas do processo seletivo do projeto \"" + project.getTitle() +
                "\". A decisão final sobre sua proposta agora é da empresa.\n\n" +
                "Acesse o Nexus para acompanhar.\n\nEquipe Nexus"
        );
    }

    // CANCELAMENTO EM CASCATA — chamado pelo MatchService em todo caminho que encerra um match
    // (recusa por qualquer lado, cancelamento por qualquer lado) ou fecha um projeto. Afeta
    // convites ainda acionáveis ou aguardando decisão (SENT/IN_PROGRESS/SUBMITTED) -- um já
    // APPROVED/REPROVED mantém seu valor histórico mesmo com o match/projeto morto, então não é
    // tocado; DECLINED/EXPIRED/CANCELLED já são terminais.

    @Transactional
    public void cancelPendingForProfessionalProject(Project project, Professional professional) {
        Optional<ScreeningQuestionnaire> questionnaireOpt =
                screeningQuestionnaireRepository.findByProjectId(project.getId());
        if (questionnaireOpt.isEmpty()) {
            return;
        }
        ScreeningStage currentStage = findCurrentStage(questionnaireOpt.get(), professional);
        if (currentStage == null) {
            return;
        }
        ScreeningInvitation latest = screeningInvitationRepository
                .findFirstByScreeningStageIdAndProfessionalIdOrderBySentAtDesc(
                        currentStage.getId(), professional.getId())
                .orElse(null);
        if (latest == null || !CANCELLABLE_STATUSES.contains(latest.getStatus())) {
            return;
        }
        cancelInvitation(latest, project);
    }

    // Varredura por projeto inteiro (não por par vaga+profissional) -- cobre profissionais que
    // ainda estavam no meio do processo sem nem ter chegado a ter um Match (ex.: vieram pelo gate
    // de enviar proposta). Chamado quando o projeto inteiro fecha.
    @Transactional
    public void cancelAllPendingForProject(Project project) {
        Optional<ScreeningQuestionnaire> questionnaireOpt =
                screeningQuestionnaireRepository.findByProjectId(project.getId());
        if (questionnaireOpt.isEmpty()) {
            return;
        }
        List<ScreeningInvitation> pending = screeningInvitationRepository
                .findByScreeningStageScreeningQuestionnaireIdAndStatusIn(
                        questionnaireOpt.get().getId(), CANCELLABLE_STATUSES);
        for (ScreeningInvitation invitation : pending) {
            cancelInvitation(invitation, project);
        }
    }

    private void cancelInvitation(ScreeningInvitation invitation, Project project) {
        invitation.setStatus(ScreeningInvitationStatus.CANCELLED);
        screeningInvitationRepository.save(invitation);
        notificationService.notifyScreeningCancelled(invitation.getProfessional().getUser(), project.getTitle());
    }

    // EXPIRAÇÃO — chamado pelo NexusScheduler

    @Transactional
    public void expirePendingInvitations() {
        List<ScreeningInvitation> expired = screeningInvitationRepository
                .findByStatusInAndDeadlineAtBefore(PENDING_STATUSES, LocalDateTime.now());

        for (ScreeningInvitation invitation : expired) {
            invitation.setStatus(ScreeningInvitationStatus.EXPIRED);
            screeningInvitationRepository.save(invitation);

            ScreeningStage stage = invitation.getScreeningStage();
            Project project = stage.getScreeningQuestionnaire().getProject();
            notificationService.notifyScreeningExpiredForCompany(
                    project.getCompany().getUser(), invitation.getProfessional().getName(),
                    project.getTitle(), stage.getTitle());
            notificationService.notifyScreeningExpiredForProfessional(
                    invitation.getProfessional().getUser(), project.getTitle(), stage.getTitle());
        }
    }

    // CONSULTAS

    public ScreeningInvitation findById(Long id) {
        return screeningInvitationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Screening invitation not found: " + id));
    }

    public List<ScreeningInvitationSummaryDTO> getSummariesFor(Project project, Professional professional) {
        return screeningInvitationRepository
                .findByScreeningStageScreeningQuestionnaireProjectIdAndProfessionalId(
                        project.getId(), professional.getId())
                .stream()
                .sorted(Comparator.comparing(ScreeningInvitation::getSentAt).reversed())
                .map(this::toSummaryDTO)
                .toList();
    }

    public List<ScreeningInvitationSummaryDTO> getSummariesForMatch(Match match) {
        return getSummariesFor(match.getProject(), match.getProfessional());
    }

    // Dentre os ids informados, quais matches ainda têm um processo seletivo em andamento por
    // trás (aparecem como pendingMatch de alguma invitation) -- usado por
    // MatchService.getInScreeningMatchesFor* pra separar a aba "Em processo" das abas normais de
    // convite/interesse pendente. Só é chamado com matches já filtrados por status
    // (WAITING/COMPANY_INTERESTED), então "tem pendingMatch" aqui já implica "ainda não resolvido"
    // -- assim que o processo termina (aprovado ou reprovado), o match sai desse status.
    public Set<Long> findMatchIdsWithActiveScreening(List<Long> matchIds) {
        if (matchIds.isEmpty()) {
            return Set.of();
        }
        return screeningInvitationRepository.findByPendingMatchIdIn(matchIds).stream()
                .map(inv -> inv.getPendingMatch().getId())
                .collect(Collectors.toSet());
    }

    // "PROCESSOS SELETIVOS" — telas de acompanhamento dos dois lados (uma linha por par
    // questionário+profissional, com todas as etapas e seus status).

    private record ProcessKey(Long screeningQuestionnaireId, Long professionalId) {}

    public List<ScreeningProcessSummaryDTO> getProcessesForProfessional(Long professionalId) {
        List<ScreeningInvitation> all = screeningInvitationRepository.findByProfessionalId(professionalId);
        return buildProcessSummaries(all);
    }

    public List<ScreeningProcessSummaryDTO> getProcessesForCompany(Long companyId) {
        List<ScreeningInvitation> all = screeningInvitationRepository
                .findByScreeningStageScreeningQuestionnaireProjectCompanyId(companyId);
        return buildProcessSummaries(all);
    }

    private List<ScreeningProcessSummaryDTO> buildProcessSummaries(List<ScreeningInvitation> invitations) {
        Map<ProcessKey, List<ScreeningInvitation>> byProcess = invitations.stream()
                .collect(Collectors.groupingBy(inv -> new ProcessKey(
                        inv.getScreeningStage().getScreeningQuestionnaire().getId(),
                        inv.getProfessional().getId())));

        List<ScreeningProcessSummaryDTO> result = new ArrayList<>();
        for (List<ScreeningInvitation> group : byProcess.values()) {
            result.add(buildProcessSummary(group));
        }
        result.sort(Comparator.comparing(ScreeningProcessSummaryDTO::lastActivityAt).reversed());
        return result;
    }

    // Tentativa mais recente por etapa -- uma etapa pode ter mais de uma (recusar/expirar permite
    // tentar de novo), só a mais nova importa pro status exibido. Compartilhado por
    // buildStageStatusList e getProcessDetail, pra nunca divergir em qual invitation representa
    // "o estado atual" de cada etapa.
    private Map<Long, ScreeningInvitation> latestInvitationByStage(List<ScreeningInvitation> invitationsForProcess) {
        Map<Long, ScreeningInvitation> latestByStage = new HashMap<>();
        for (ScreeningInvitation invitation : invitationsForProcess) {
            Long stageId = invitation.getScreeningStage().getId();
            ScreeningInvitation current = latestByStage.get(stageId);
            if (current == null || invitation.getSentAt().isAfter(current.getSentAt())) {
                latestByStage.put(stageId, invitation);
            }
        }
        return latestByStage;
    }

    // Status de cada etapa do questionário pra ESTE profissional (etapa não alcançada ainda =
    // status/invitationId nulos) -- usado tanto pelo acompanhamento "Processos Seletivos"
    // (buildProcessSummary) quanto pelo fluxo de etapas na tela de detalhe de uma invitation
    // (toDetailDTO), pra nunca divergir entre as duas telas.
    private List<ScreeningStageStatusDTO> buildStageStatusList(
            ScreeningQuestionnaire questionnaire, List<ScreeningInvitation> invitationsForProcess) {
        Map<Long, ScreeningInvitation> latestByStage = latestInvitationByStage(invitationsForProcess);

        List<ScreeningStageStatusDTO> stages = new ArrayList<>();
        for (ScreeningStage stage : questionnaire.getStages()) {
            ScreeningInvitation invitation = latestByStage.get(stage.getId());
            // Etapa removida (active=false) que esse profissional nunca alcançou -- não é dele,
            // não aparece no acompanhamento.
            if (invitation == null && !Boolean.TRUE.equals(stage.getActive())) {
                continue;
            }
            stages.add(new ScreeningStageStatusDTO(
                    stage.getId(),
                    stage.getOrderIndex(),
                    stage.getTitle(),
                    invitation != null ? invitation.getStatus() : null,
                    invitation != null ? invitation.getId() : null
            ));
        }
        return stages;
    }

    private ScreeningProcessSummaryDTO buildProcessSummary(List<ScreeningInvitation> invitationsForProcess) {
        ScreeningQuestionnaire questionnaire = invitationsForProcess.get(0)
                .getScreeningStage().getScreeningQuestionnaire();
        Professional professional = invitationsForProcess.get(0).getProfessional();
        Project project = questionnaire.getProject();

        List<ScreeningStageStatusDTO> stages = buildStageStatusList(questionnaire, invitationsForProcess);

        ScreeningInvitation latestOverall = invitationsForProcess.stream()
                .max(Comparator.comparing(ScreeningInvitation::getSentAt))
                .orElseThrow();

        int currentStageOrderIndex = 1;
        for (int i = 0; i < stages.size(); i++) {
            if (latestOverall.getId().equals(stages.get(i).invitationId())) {
                currentStageOrderIndex = i + 1;
                break;
            }
        }

        return new ScreeningProcessSummaryDTO(
                questionnaire.getId(),
                project.getId(),
                project.getTitle(),
                project.getOpportunityType(),
                professional.getId(),
                professional.getName(),
                professional.getProfilePhotoUrl(),
                professional.getReputation(),
                project.getCompany().getId(),
                project.getCompany().getCompanyName(),
                project.getCompany().getProfilePhotoUrl(),
                latestOverall.getStatus(),
                latestOverall.getId(),
                currentStageOrderIndex,
                stages.size(),
                latestOverall.getSentAt(),
                stages,
                null
        );
    }

    // VALIDAÇÕES DE POSSE

    private void validateProfessionalOwnership(ScreeningInvitation invitation, Long professionalId) {
        if (!invitation.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This screening invitation does not belong to you.");
        }
    }

    public void validateCompanyOwnership(ScreeningInvitation invitation, Long companyId) {
        if (!invitation.getScreeningStage().getScreeningQuestionnaire().getProject()
                .getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This screening invitation does not belong to your company.");
        }
    }

    public void validateParticipant(ScreeningInvitation invitation, Long companyId, Long professionalId) {
        if (companyId != null) {
            validateCompanyOwnership(invitation, companyId);
        } else if (professionalId != null) {
            validateProfessionalOwnership(invitation, professionalId);
        } else {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not authorized to view this screening invitation.");
        }
    }

    private void assertPending(ScreeningInvitation invitation) {
        if (!PENDING_STATUSES.contains(invitation.getStatus())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This screening invitation is no longer pending a response.");
        }
    }

    // CONVERSÃO PRA DTO

    public ScreeningAttemptDTO toAttemptDTO(ScreeningInvitation invitation) {
        ScreeningStage stage = invitation.getScreeningStage();
        ScreeningQuestionnaire questionnaire = stage.getScreeningQuestionnaire();
        Project project = questionnaire.getProject();

        List<ScreeningAttemptQuestionDTO> questions = stage.getQuestions().stream()
                .filter(ScreeningQuestion::getActive)
                .map(q -> new ScreeningAttemptQuestionDTO(q.getId(), q.getType(), q.getPrompt(), q.getOptions()))
                .toList();

        return new ScreeningAttemptDTO(
                invitation.getId(),
                questionnaire.getTitle(),
                questionnaire.getInstructions(),
                stage.getTitle(),
                stageDisplayRank(questionnaire, stage),
                countDisplayStages(questionnaire, stage),
                stage.getInstructions(),
                invitation.getStatus(),
                invitation.getDeadlineAt(),
                project.getTitle(),
                project.getCompany().getCompanyName(),
                questions
        );
    }

    public ScreeningInvitationSummaryDTO toSummaryDTO(ScreeningInvitation invitation) {
        ScreeningStage stage = invitation.getScreeningStage();
        ScreeningQuestionnaire questionnaire = stage.getScreeningQuestionnaire();
        return new ScreeningInvitationSummaryDTO(
                invitation.getId(),
                questionnaire.getId(),
                questionnaire.getTitle(),
                stage.getTitle(),
                stageDisplayRank(questionnaire, stage),
                countDisplayStages(questionnaire, stage),
                invitation.getStatus(),
                invitation.getSentAt(),
                invitation.getDeadlineAt(),
                invitation.getSubmittedAt(),
                invitation.getAutoScorePercent()
        );
    }

    // forCompany decide se tabSwitchCount é populado -- decisão confirmada de manter esse sinal
    // visível só para o contratante.
    public ScreeningInvitationDetailDTO toDetailDTO(ScreeningInvitation invitation, boolean forCompany) {
        ScreeningStage stage = invitation.getScreeningStage();
        ScreeningQuestionnaire questionnaire = stage.getScreeningQuestionnaire();
        Project project = questionnaire.getProject();
        Professional professional = invitation.getProfessional();

        List<ScreeningAnswerDetailDTO> answers = invitation.getAnswers().stream()
                .map(a -> new ScreeningAnswerDetailDTO(
                        a.getId(),
                        a.getScreeningQuestion().getId(),
                        a.getScreeningQuestion().getType(),
                        a.getScreeningQuestion().getPrompt(),
                        a.getScreeningQuestion().getOptions(),
                        a.getSelectedOptionIndex(),
                        a.getScreeningQuestion().getCorrectOptionIndex(),
                        a.getCorrect(),
                        a.getEssayText(),
                        a.getTimeSpentSeconds()
                ))
                .toList();

        List<ScreeningInvitation> allForProcess = screeningInvitationRepository
                .findByScreeningStageScreeningQuestionnaireProjectIdAndProfessionalId(
                        project.getId(), professional.getId());
        List<ScreeningStageStatusDTO> stages = buildStageStatusList(questionnaire, allForProcess);

        return new ScreeningInvitationDetailDTO(
                invitation.getId(),
                questionnaire.getId(),
                questionnaire.getTitle(),
                questionnaire.getInstructions(),
                stage.getId(),
                stage.getTitle(),
                stageDisplayRank(questionnaire, stage),
                countDisplayStages(questionnaire, stage),
                stage.getInstructions(),
                project.getId(),
                project.getTitle(),
                professional.getId(),
                professional.getName(),
                invitation.getStatus(),
                invitation.getSentAt(),
                invitation.getDeadlineAt(),
                invitation.getStartedAt(),
                invitation.getSubmittedAt(),
                invitation.getDecidedAt(),
                invitation.getTotalTimeSpentSeconds(),
                forCompany ? invitation.getTabSwitchCount() : null,
                invitation.getAutoScorePercent(),
                invitation.getCompanyDecisionComment(),
                invitation.getPendingIntentType(),
                invitation.getPendingProposal() != null ? invitation.getPendingProposal().getId() : null,
                answers,
                stages
        );
    }

    // Detalhe completo (com respostas) de TODAS as etapas já alcançadas deste processo, não só a
    // de `anchorInvitationId` -- base da tela de detalhe (empresa decidindo, profissional
    // acompanhando), que agora mostra o processo inteiro como um fluxo, não uma etapa isolada.
    // Etapas ainda não alcançadas não entram aqui (o front já sabe da existência delas via
    // ScreeningInvitationDetailDTO.stages, que toDetailDTO preenche pra cada uma retornada).
    public List<ScreeningInvitationDetailDTO> getProcessDetail(Long anchorInvitationId, boolean forCompany) {
        ScreeningInvitation anchor = findById(anchorInvitationId);
        ScreeningQuestionnaire questionnaire = anchor.getScreeningStage().getScreeningQuestionnaire();
        Professional professional = anchor.getProfessional();
        Project project = questionnaire.getProject();

        List<ScreeningInvitation> allForProcess = screeningInvitationRepository
                .findByScreeningStageScreeningQuestionnaireProjectIdAndProfessionalId(
                        project.getId(), professional.getId());
        Map<Long, ScreeningInvitation> latestByStage = latestInvitationByStage(allForProcess);

        List<ScreeningInvitationDetailDTO> result = new ArrayList<>();
        for (ScreeningStage stage : questionnaire.getStages()) {
            ScreeningInvitation invitation = latestByStage.get(stage.getId());
            if (invitation != null) {
                result.add(toDetailDTO(invitation, forCompany));
            }
        }
        return result;
    }

    // Posição de `stage` entre as etapas "visíveis" (ativas, mais a própria mesmo se ela tiver
    // sido desativada depois de já estar em andamento nela -- pra nunca mostrar uma etapa fora
    // da contagem total).
    private int stageDisplayRank(ScreeningQuestionnaire questionnaire, ScreeningStage stage) {
        int rank = 0;
        for (ScreeningStage s : questionnaire.getStages()) {
            if (Boolean.TRUE.equals(s.getActive()) || s.getId().equals(stage.getId())) {
                rank++;
            }
            if (s.getId().equals(stage.getId())) {
                return rank;
            }
        }
        return rank;
    }

    private int countDisplayStages(ScreeningQuestionnaire questionnaire, ScreeningStage current) {
        int count = 0;
        for (ScreeningStage s : questionnaire.getStages()) {
            if (Boolean.TRUE.equals(s.getActive()) || s.getId().equals(current.getId())) {
                count++;
            }
        }
        return count;
    }
}
