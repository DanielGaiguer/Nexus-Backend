package com.main.nexus.service;

import com.main.nexus.dto.ProposalAttachmentDTO;
import com.main.nexus.dto.ProposalRequestDTO;
import com.main.nexus.dto.ProposalResponseDTO;
import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Proposal;
import com.main.nexus.model.ProposalAttachment;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.InitiatedBy;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.PendingIntentType;
import com.main.nexus.model.enums.ProposalStatus;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ProposalRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Propostas de execução para oportunidades PROJECT com acceptsProposals = true. Coexiste com o
// fluxo de match bilateral (MatchService) sem alterá-lo -- ver o comentário de acceptProposal()
// pra como as duas coisas se encaixam no aceite.
@Service
public class ProposalService {

    private static final int MAX_ATTACHMENTS = 5;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    @Autowired
    private SkillService skillService;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchHistoryService matchHistoryService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

    // ENVIO E EDIÇÃO

    @Transactional
    public Proposal submitProposal(ProposalRequestDTO request, Long professionalId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));

        validateAcceptsProposals(project);
        matchService.assertProjectIsOpen(project);

        boolean alreadyPending = proposalRepository
                .findByProjectIdAndProfessionalIdAndStatusIn(
                        project.getId(), professionalId, List.of(ProposalStatus.PENDING))
                .isPresent();
        if (alreadyPending) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "You already have a pending proposal for this project.");
        }

        Proposal proposal = new Proposal();
        proposal.setProject(project);
        proposal.setProfessional(professional);
        applyRequest(proposal, request);
        // Snapshot do score já calculado pelo motor de match existente -- nunca recalculado do
        // zero (RN da feature).
        proposal.setMatchScoreAtSubmission(matchService.getScore(professional, project));

        // Salva antes do gate pra ter um id (o processo seletivo em etapas, se a vaga tiver um,
        // referencia essa proposta como pendingProposal -- ver PendingIntentType.PROPOSAL_SUBMIT).
        Proposal saved = proposalRepository.save(proposal);

        // A proposta NUNCA fica escondida da empresa, mesmo que a vaga tenha um processo em
        // etapas -- decisão confirmada com o usuário: o aceite/recusa da proposta é sempre uma
        // ação independente dela, feita a qualquer momento, sem depender do resultado das etapas.
        // checkGate só serve aqui pra gerar/anexar o convite da 1ª etapa (o profissional ainda é
        // levado a respondê-la, mas isso não trava a visibilidade nem o status da proposta).
        screeningInvitationService.checkGate(
                project, professional, PendingIntentType.PROPOSAL_SUBMIT, null, saved);

        sendProposalReceivedNotification(saved);
        return saved;
    }

    private void sendProposalReceivedNotification(Proposal proposal) {
        Project project = proposal.getProject();
        Professional professional = proposal.getProfessional();
        notificationService.notifyProposalReceived(
                project.getCompany().getUser(), professional.getName(), project.getTitle(), project.getId());
        emailService.send(
                project.getCompany().getUser().getEmail(),
                "Nova proposta recebida — Nexus",
                "Olá " + project.getCompany().getCompanyName() + ",\n\n" +
                professional.getName() + " enviou uma proposta para o seu projeto \"" + project.getTitle() + "\".\n\n" +
                "Acesse o Nexus para ver os detalhes e comparar com os demais candidatos.\n\nEquipe Nexus"
        );
    }

    @Transactional
    public Proposal updateProposal(Long proposalId, ProposalRequestDTO request, Long professionalId) {
        Proposal proposal = findById(proposalId);
        validateProfessionalOwnership(proposal, professionalId);
        assertPending(proposal, "edited");

        applyRequest(proposal, request);
        // Perfil pode ter mudado desde o envio original -- re-snapshotta pra refletir o estado
        // atual no momento da edição.
        proposal.setMatchScoreAtSubmission(
                matchService.getScore(proposal.getProfessional(), proposal.getProject()));
        proposal.setUpdatedAt(LocalDateTime.now());

        return proposalRepository.save(proposal);
    }

    @Transactional
    public Proposal withdrawProposal(Long proposalId, Long professionalId) {
        Proposal proposal = findById(proposalId);
        validateProfessionalOwnership(proposal, professionalId);
        assertPending(proposal, "withdrawn");

        proposal.setStatus(ProposalStatus.WITHDRAWN);
        return proposalRepository.save(proposal);
    }

    private void applyRequest(Proposal proposal, ProposalRequestDTO request) {
        proposal.setProposedValue(request.proposedValue());
        proposal.setEstimatedDays(request.estimatedDays());
        proposal.setProposedStartDate(request.proposedStartDate());
        proposal.setProposedDeliveryDate(request.proposedDeliveryDate());
        proposal.setDescription(request.description());
        proposal.setRelevantExperience(request.relevantExperience());
        if (request.skillIds() != null) {
            proposal.setSkills(skillService.findAllById(request.skillIds()));
        }
        proposal.setDeliverables(request.deliverables());
        proposal.setExecutionSteps(request.executionSteps());
        proposal.setPaymentTerms(request.paymentTerms());

        Integer validityDays = request.validityDays();
        if (validityDays == null || validityDays <= 0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "'validityDays' must be a positive number.");
        }
        proposal.setValidityDays(validityDays);
        proposal.setExpiresAt(LocalDateTime.now().plusDays(validityDays));
        proposal.setQuestionsForCompany(request.questionsForCompany());
    }

    private void validateAcceptsProposals(Project project) {
        if (project.getOpportunityType() != OpportunityType.PROJECT
                || !Boolean.TRUE.equals(project.getAcceptsProposals())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This opportunity does not accept proposals.");
        }
    }

    private void assertPending(Proposal proposal, String action) {
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only a pending proposal can be " + action + ".");
        }
    }

    // ANEXOS

    @Transactional
    public Proposal addAttachments(Long proposalId, Long professionalId, List<MultipartFile> files) {
        Proposal proposal = findById(proposalId);
        validateProfessionalOwnership(proposal, professionalId);
        assertPending(proposal, "changed");

        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "At least one file is required.");
        }
        if (proposal.getAttachments().size() + files.size() > MAX_ATTACHMENTS) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A proposal can have at most " + MAX_ATTACHMENTS + " attachments.");
        }

        for (MultipartFile file : files) {
            String url = supabaseStorageService.uploadProposalAttachment(file, proposal.getId());
            ProposalAttachment attachment = new ProposalAttachment();
            attachment.setProposal(proposal);
            attachment.setFileUrl(url);
            attachment.setFileName(file.getOriginalFilename());
            proposal.getAttachments().add(attachment);
        }

        return proposalRepository.save(proposal);
    }

    @Transactional
    public void removeAttachment(Long proposalId, Long attachmentId, Long professionalId) {
        Proposal proposal = findById(proposalId);
        validateProfessionalOwnership(proposal, professionalId);
        assertPending(proposal, "changed");

        ProposalAttachment attachment = proposal.getAttachments().stream()
                .filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Attachment not found."));

        supabaseStorageService.deleteProposalAttachment(attachment.getFileUrl());
        proposal.getAttachments().remove(attachment);
        proposalRepository.save(proposal);
    }

    // CONSULTAS

    public Proposal findById(Long id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Proposal not found: " + id));
    }

    public List<Proposal> listProposalsForProject(Long projectId, Long companyId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }
        return proposalRepository.findByProjectId(projectId);
    }

    public List<Proposal> listProposalsForProfessional(Long professionalId) {
        return proposalRepository.findByProfessionalId(professionalId);
    }

    // Visão geral da empresa (/company/proposals) -- todas as propostas recebidas em
    // qualquer projeto seu, mais recentes primeiro.
    public List<Proposal> listProposalsForCompany(Long companyId) {
        return proposalRepository.findByProjectCompanyId(companyId).stream()
                .sorted(Comparator.comparing(Proposal::getCreatedAt).reversed())
                .toList();
    }

    // Detalhe de uma proposta -- participante validado (dona do projeto ou autor da proposta),
    // mesmo padrão de MatchController.validateParticipant.
    public Proposal getForParticipant(Long proposalId, Long companyId, Long professionalId) {
        Proposal proposal = findById(proposalId);
        if (companyId != null) {
            validateCompanyOwnership(proposal, companyId);
        } else if (professionalId != null) {
            validateProfessionalOwnership(proposal, professionalId);
        } else {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not authorized to view this proposal.");
        }
        return proposal;
    }

    // ACEITE / RECUSA

    // Aceitar uma proposta é o único evento (fora do fluxo bilateral normal) que força um Match
    // pra MATCHED -- localiza ou cria o Match do par (mesmo padrão find-or-create de
    // MatchService.companyShowsIntcerestByProject), sem exigir que o fluxo de interesse
    // bilateral tenha avançado antes (é justamente a limitação que a proposta resolve).
    @Transactional
    public Match acceptProposal(Long proposalId, Long companyId) {
        Proposal proposal = findById(proposalId);
        validateCompanyOwnership(proposal, companyId);
        assertPending(proposal, "accepted");

        Project project = proposal.getProject();
        Professional professional = proposal.getProfessional();

        matchService.assertProjectHasOpenPositions(project);

        Match match = matchRepository
                .findByProjectIdAndProfessionalId(project.getId(), professional.getId())
                .orElseGet(() -> {
                    Match newMatch = new Match();
                    newMatch.setProject(project);
                    newMatch.setProfessional(professional);
                    newMatch.setMatchScore(matchService.getScore(professional, project));
                    newMatch.setInitiatedBy(InitiatedBy.PROFESSIONAL);
                    return newMatch;
                });

        if (match.getStatus() == StatusMatch.REJECTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match was rejected and cannot be reactivated by accepting a proposal.");
        }

        String fromStatus = match.getStatus().name();
        match.setCompanyStatus(InterestStatus.INTERESTED);
        match.setProfessionalStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.MATCHED);
        match.setAcceptedProposal(proposal);
        if (match.getInitiatedBy() == null) {
            match.setInitiatedBy(InitiatedBy.PROFESSIONAL);
        }

        // Mesmo efeito colateral (incrementa filledPositions e pausa o projeto se esgotar) que
        // o fluxo bilateral já usa em companyAccepts/professionalAccepts.
        matchService.incrementFilledPositions(project);
        // Salva antes de registrar o histórico -- quando o Match acabou de ser criado no
        // orElseGet acima (par sem Match bilateral prévio), ele ainda é uma instância transiente;
        // gravar o histórico antes do save falha (MatchHistory.match apontaria pra algo não
        // persistido ainda).
        Match savedMatch = matchRepository.save(match);
        matchHistoryService.record(savedMatch, fromStatus, savedMatch.getStatus().name(), "COMPANY");

        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposalRepository.save(proposal);

        notificationService.notifyProposalAccepted(
                professional.getUser(), project.getCompany().getCompanyName(), project.getTitle(), savedMatch.getId());
        emailService.send(
                professional.getUser().getEmail(),
                "Proposta aceita! — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                project.getCompany().getCompanyName() + " aceitou sua proposta para o projeto \"" +
                project.getTitle() + "\".\n\n" +
                "O match foi confirmado e os contatos já estão disponíveis no Nexus.\n\nEquipe Nexus"
        );

        // Se essa aceitação esgotou as vagas do projeto, as demais propostas pendentes não têm
        // mais como prosseguir -- viram REJECTED, mas rotuladas como "vaga preenchida" (não como
        // recusa ativa da empresa), com notificação e e-mail distintos.
        if (!project.hasOpenPositions()) {
            autoRejectRemainingProposals(project, proposal.getId());
        }

        return savedMatch;
    }

    private void autoRejectRemainingProposals(Project project, Long acceptedProposalId) {
        List<Proposal> pending = proposalRepository
                .findByProjectIdAndStatus(project.getId(), ProposalStatus.PENDING);

        for (Proposal other : pending) {
            if (other.getId().equals(acceptedProposalId)) continue;

            other.setStatus(ProposalStatus.REJECTED);
            other.setAutoRejectedPositionFilled(true);
            proposalRepository.save(other);

            Professional otherProfessional = other.getProfessional();
            notificationService.notifyProposalPositionFilled(
                    otherProfessional.getUser(), project.getCompany().getCompanyName(), project.getTitle());
            emailService.send(
                    otherProfessional.getUser().getEmail(),
                    "Vaga preenchida — Nexus",
                    "Olá " + otherProfessional.getName() + ",\n\n" +
                    "O projeto \"" + project.getTitle() + "\" de " + project.getCompany().getCompanyName() +
                    " já teve suas vagas preenchidas por outra proposta. Sua proposta não segue mais em análise " +
                    "— isso não é um reflexo da qualidade dela, foi uma questão de ordem de aceite.\n\nEquipe Nexus"
            );
        }
    }

    @Transactional
    public Proposal rejectProposal(Long proposalId, Long companyId) {
        Proposal proposal = findById(proposalId);
        validateCompanyOwnership(proposal, companyId);
        assertPending(proposal, "rejected");

        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setAutoRejectedPositionFilled(false);
        Proposal saved = proposalRepository.save(proposal);

        Professional professional = proposal.getProfessional();
        Project project = proposal.getProject();
        notificationService.notifyProposalRejected(
                professional.getUser(), project.getCompany().getCompanyName(), project.getTitle());
        emailService.send(
                professional.getUser().getEmail(),
                "Proposta recusada — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                project.getCompany().getCompanyName() + " recusou sua proposta para o projeto \"" +
                project.getTitle() + "\".\n\nEquipe Nexus"
        );

        return saved;
    }

    // EXPIRAÇÃO — chamado pelo NexusScheduler

    @Transactional
    public void expirePendingProposals() {
        List<Proposal> expired = proposalRepository
                .findByStatusAndExpiresAtBefore(ProposalStatus.PENDING, LocalDateTime.now());

        for (Proposal proposal : expired) {
            proposal.setStatus(ProposalStatus.EXPIRED);
            proposalRepository.save(proposal);

            Professional professional = proposal.getProfessional();
            notificationService.notifyProposalExpired(professional.getUser(), proposal.getProject().getTitle());
            emailService.send(
                    professional.getUser().getEmail(),
                    "Sua proposta expirou — Nexus",
                    "Olá " + professional.getName() + ",\n\n" +
                    "O prazo de validade da sua proposta para o projeto \"" + proposal.getProject().getTitle() +
                    "\" terminou sem resposta do contratante.\n\n" +
                    "Você pode enviar uma nova proposta se ainda tiver interesse.\n\nEquipe Nexus"
            );
        }
    }

    // VALIDAÇÕES DE POSSE

    private void validateProfessionalOwnership(Proposal proposal, Long professionalId) {
        if (!proposal.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This proposal does not belong to you.");
        }
    }

    private void validateCompanyOwnership(Proposal proposal, Long companyId) {
        if (!proposal.getProject().getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This proposal does not belong to your company.");
        }
    }

    // CONVERSÃO PRA DTO

    // Reaproveitado pelo ProposalController e por MatchController/ProjectController (pra
    // popular MatchResponseDTO.acceptedProposal). Monta também os campos de comparação
    // (reputação, projetos anteriores, skills compatíveis/faltantes) com a mesma lógica que
    // CandidateComparisonService.buildComparisonItem já usa pro ranking de candidatos, só que
    // sem depender de um Match existir.
    public ProposalResponseDTO toResponseDTO(Proposal proposal) {
        Professional professional = proposal.getProfessional();
        Project project = proposal.getProject();

        List<String> proposalSkillNames = proposal.getSkills().stream().map(Skill::getName).toList();
        List<String> requiredSkillNames = project.getRequiredSkills().stream().map(Skill::getName).toList();

        List<String> matchingSkills = requiredSkillNames.stream()
                .filter(s -> proposalSkillNames.stream().anyMatch(ps -> ps.equalsIgnoreCase(s)))
                .toList();
        List<String> missingSkills = requiredSkillNames.stream()
                .filter(s -> proposalSkillNames.stream().noneMatch(ps -> ps.equalsIgnoreCase(s)))
                .toList();

        // totalReviews vem de ReputationMetrics (cache derivado, RN66); a nota em si
        // (professional.getReputation(), escala 1-5) é a mesma já exibida em qualquer outro
        // lugar do sistema (ProfessionalSummaryDTO, CandidateComparisonItemDTO) -- não o
        // reputationScore 0-100 usado internamente pelo motor de match.
        ReputationMetrics metrics = reputationMetricsRepository
                .findByProfessionalId(professional.getId())
                .orElse(null);

        Long matchId = matchRepository
                .findByProjectIdAndProfessionalId(project.getId(), professional.getId())
                .map(Match::getId)
                .orElse(null);

        return new ProposalResponseDTO(
                proposal.getId(),
                project.getId(),
                project.getTitle(),
                matchId,

                professional.getId(),
                professional.getName(),
                professional.getCity(),
                professional.getUf(),
                professional.getProfilePhotoUrl(),
                professional.getExperienceLevel(),

                proposal.getProposedValue(),
                proposal.getEstimatedDays(),
                proposal.getProposedStartDate(),
                proposal.getProposedDeliveryDate(),
                proposal.getDescription(),
                proposal.getRelevantExperience(),
                proposal.getSkills().stream()
                        .map(s -> new SkillResponseDTO(s.getId(), s.getName(), s.getCategory()))
                        .toList(),
                proposal.getDeliverables(),
                proposal.getExecutionSteps(),
                proposal.getPaymentTerms(),
                proposal.getValidityDays(),
                proposal.getExpiresAt(),
                proposal.getQuestionsForCompany(),
                proposal.getAttachments().stream()
                        .map(a -> new ProposalAttachmentDTO(a.getId(), a.getFileUrl(), a.getFileName()))
                        .toList(),

                proposal.getStatus(),
                proposal.getMatchScoreAtSubmission(),
                proposal.getAutoRejectedPositionFilled(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt(),

                professional.getReputation(),
                metrics != null ? metrics.getTotalReviews() : 0,
                professional.getProjects() != null ? professional.getProjects().size() : 0,
                matchingSkills,
                missingSkills,

                screeningInvitationService.getSummariesFor(project, professional),

                matchService.getScoreBreakdownForCandidate(professional.getId(), project.getId())
        );
    }
}
