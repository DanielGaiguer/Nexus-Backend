package com.main.nexus.controller;

import com.main.nexus.dto.ScreeningAttemptDTO;
import com.main.nexus.dto.ScreeningInvitationDetailDTO;
import com.main.nexus.dto.ScreeningProcessSummaryDTO;
import com.main.nexus.dto.ScreeningStageDecisionRequestDTO;
import com.main.nexus.dto.ScreeningSubmissionRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.enums.CompanyRejectionReason;
import com.main.nexus.model.enums.PendingIntentType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ScreeningInvitationService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/screening-invitations")
public class ScreeningInvitationController {

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private CompanyService companyService;

    // Só pra retomar a ação que ficou pendente depois da última etapa ser aprovada, e pra
    // encerrar o match quando uma etapa é reprovada (ver PendingIntentType) -- não cria
    // dependência circular porque controllers nunca são injetados em services.
    @Autowired
    private MatchService matchService;

    // EMPRESA — decisão por etapa

    @PostMapping("/{id}/approve")
    public ResponseEntity<ScreeningInvitationDetailDTO> approve(
            @PathVariable Long id, @RequestBody ScreeningStageDecisionRequestDTO request) {
        ScreeningInvitationService.StageDecision decision =
                screeningInvitationService.approveStage(id, getLoggedCompanyId(), request.comment());

        // Só retoma quando essa era a última etapa -- interesse/aceite viram a ação de verdade;
        // proposta nunca é retomada automaticamente (decisão confirmada com o usuário).
        if (decision.wasLastStage()) {
            resumePendingIntent(decision.invitation());
        }
        return ResponseEntity.ok(screeningInvitationService.toDetailDTO(decision.invitation(), true));
    }

    @PostMapping("/{id}/reprove")
    public ResponseEntity<ScreeningInvitationDetailDTO> reprove(
            @PathVariable Long id, @RequestBody ScreeningStageDecisionRequestDTO request) {
        ScreeningInvitation invitation =
                screeningInvitationService.reproveStage(id, getLoggedCompanyId(), request.comment());

        // Reprovar fecha o processo na hora pra interesse/aceite (recusa formal do match, que já
        // cancela em cascata qualquer etapa pendente) -- proposta nunca é tocada, ela continua
        // livre pra a empresa aceitar ou recusar por conta própria.
        PendingIntentType intent = invitation.getPendingIntentType();
        if (invitation.getPendingMatch() != null
                && (intent == PendingIntentType.MATCH_INTEREST || intent == PendingIntentType.MATCH_ACCEPT)) {
            matchService.companyRejectsWithFeedback(
                    invitation.getPendingMatch().getId(), getLoggedCompanyId(),
                    List.of(CompanyRejectionReason.FAILED_INTERVIEW_STAGE), request.comment());
        }
        return ResponseEntity.ok(screeningInvitationService.toDetailDTO(invitation, true));
    }

    // PROFISSIONAL — resposta

    @PostMapping("/{id}/start")
    public ResponseEntity<ScreeningAttemptDTO> start(@PathVariable Long id) {
        ScreeningInvitation invitation = screeningInvitationService.startAttempt(id, getLoggedProfessionalId());
        return ResponseEntity.ok(screeningInvitationService.toAttemptDTO(invitation));
    }

    @GetMapping("/{id}/attempt")
    public ResponseEntity<ScreeningAttemptDTO> attempt(@PathVariable Long id) {
        ScreeningInvitation invitation = screeningInvitationService.findById(id);
        screeningInvitationService.validateParticipant(invitation, null, getLoggedProfessionalId());
        return ResponseEntity.ok(screeningInvitationService.toAttemptDTO(invitation));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<String> decline(@PathVariable Long id) {
        screeningInvitationService.decline(id, getLoggedProfessionalId());
        return ResponseEntity.ok("Screening invitation declined.");
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ScreeningInvitationDetailDTO> submit(
            @PathVariable Long id, @RequestBody ScreeningSubmissionRequestDTO request) {
        ScreeningInvitation invitation = screeningInvitationService.submit(id, getLoggedProfessionalId(), request);
        return ResponseEntity.ok(screeningInvitationService.toDetailDTO(invitation, false));
    }

    // Retomada automática da ação que ficou esperando o processo de etapas terminar aprovado
    // (ver PendingIntentType) -- best-effort: re-busca o Match fresco por id em vez de
    // reaproveitar a referência já carregada (que veio de uma transação já commitada em
    // ScreeningInvitationService.approveStage), e delega pro núcleo "não-estrito" de cada
    // método, que não falha se o estado mudou nesse meio tempo.
    private void resumePendingIntent(ScreeningInvitation invitation) {
        PendingIntentType intent = invitation.getPendingIntentType();
        if (intent == null) {
            return;
        }
        switch (intent) {
            case MATCH_INTEREST -> {
                if (invitation.getPendingMatch() != null) {
                    Match match = matchService.findById(invitation.getPendingMatch().getId());
                    matchService.applyProfessionalInterest(match, false);
                }
            }
            case MATCH_ACCEPT -> {
                if (invitation.getPendingMatch() != null) {
                    Match match = matchService.findById(invitation.getPendingMatch().getId());
                    matchService.applyProfessionalAccept(match, false);
                }
            }
            case PROPOSAL_SUBMIT -> {
                // Nunca retomado automaticamente -- decisão confirmada com o usuário: aceite/
                // recusa de proposta é sempre uma ação independente da empresa. Mesmo assim, o
                // profissional precisa saber que terminou de responder tudo.
                screeningInvitationService.notifyProposalScreeningCompleted(invitation);
            }
        }
    }

    // PROCESSOS SELETIVOS — telas de acompanhamento ("Processos Seletivos" no menu, acima de
    // Conversas nos dois papéis). Uma linha por processo (todas as etapas de um questionário,
    // pra um profissional), não por invitation solta.

    @GetMapping("/mine")
    public ResponseEntity<List<ScreeningProcessSummaryDTO>> myProcesses() {
        List<ScreeningProcessSummaryDTO> processes =
                screeningInvitationService.getProcessesForProfessional(getLoggedProfessionalId());
        return ResponseEntity.ok(processes.stream().map(this::withScoreBreakdown).toList());
    }

    @GetMapping("/company/mine")
    public ResponseEntity<List<ScreeningProcessSummaryDTO>> companyProcesses() {
        List<ScreeningProcessSummaryDTO> processes =
                screeningInvitationService.getProcessesForCompany(getLoggedCompanyId());
        return ResponseEntity.ok(processes.stream().map(this::withScoreBreakdown).toList());
    }

    // Preenchido aqui (não em ScreeningInvitationService) pelo mesmo motivo de MatchService estar
    // injetado neste controller -- ver comentário no topo da classe.
    private ScreeningProcessSummaryDTO withScoreBreakdown(ScreeningProcessSummaryDTO process) {
        return process.withScoreBreakdown(
                matchService.getScoreBreakdownForCandidate(process.professionalId(), process.projectId()));
    }

    // COMPARTILHADO

    @GetMapping("/{id}")
    public ResponseEntity<ScreeningInvitationDetailDTO> findById(@PathVariable Long id) {
        UserDTO logged = getLoggedUser();
        boolean isCompany = "COMPANY".equals(logged.role());
        Long companyId = isCompany ? getLoggedCompanyId() : null;
        Long professionalId = "PROFESSIONAL".equals(logged.role()) ? getLoggedProfessionalId() : null;

        ScreeningInvitation invitation = screeningInvitationService.findById(id);
        screeningInvitationService.validateParticipant(invitation, companyId, professionalId);

        boolean stillPending = invitation.getStatus() == ScreeningInvitationStatus.SENT
                || invitation.getStatus() == ScreeningInvitationStatus.IN_PROGRESS;
        if (!isCompany && stillPending) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This screening has not been submitted yet.");
        }

        return ResponseEntity.ok(screeningInvitationService.toDetailDTO(invitation, isCompany));
    }

    // Detalhe completo (com respostas) de todas as etapas já alcançadas do processo inteiro --
    // `id` só serve de âncora pra achar o par questionário+profissional, pode ser o id de
    // qualquer etapa dele. Base da tela de detalhe, que mostra o fluxo do processo inteiro.
    @GetMapping("/{id}/process")
    public ResponseEntity<List<ScreeningInvitationDetailDTO>> processDetail(@PathVariable Long id) {
        UserDTO logged = getLoggedUser();
        boolean isCompany = "COMPANY".equals(logged.role());
        Long companyId = isCompany ? getLoggedCompanyId() : null;
        Long professionalId = "PROFESSIONAL".equals(logged.role()) ? getLoggedProfessionalId() : null;

        ScreeningInvitation anchor = screeningInvitationService.findById(id);
        screeningInvitationService.validateParticipant(anchor, companyId, professionalId);

        return ResponseEntity.ok(screeningInvitationService.getProcessDetail(id, isCompany));
    }

    // UTILITÁRIOS DE IDENTIDADE — mesmo padrão de MatchController/ProposalController

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private Long getLoggedCompanyId() {
        UserDTO logged = getLoggedUser();
        return companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"))
                .getId();
    }

    private Long getLoggedProfessionalId() {
        UserDTO logged = getLoggedUser();
        return professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional profile not found"))
                .getId();
    }
}
