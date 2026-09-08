package com.main.nexus.service;

import com.main.nexus.dto.PipelineBoardDTO;
import com.main.nexus.dto.PipelineCardColumnDTO;
import com.main.nexus.dto.PipelineCardDTO;
import com.main.nexus.dto.PipelineCardEvaluationDTO;
import com.main.nexus.dto.PipelineStageDTO;
import com.main.nexus.dto.PipelineStageHistoryDTO;
import com.main.nexus.dto.PipelineStageRequestDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ScreeningInvitationSummaryDTO;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.PipelineStage;
import com.main.nexus.model.PipelineStageHistory;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.Skill;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.MatchConfirmationRepository;
import com.main.nexus.repository.MatchHistoryRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.PipelineStageHistoryRepository;
import com.main.nexus.repository.PipelineStageRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.RejectionFeedbackRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Fundação do Kanban de contratação (Passo 1). Duas responsabilidades:
//  - as etapas intermediárias configuráveis por vaga (PipelineStage) -- CRUD/reordenação/
//    soft-delete no mesmo molde de ScreeningQuestionnaireService.mergeStages, e seed lazy de 5
//    etapas default na primeira leitura do board (padrão de CommissionService.getPolicy);
//  - a montagem do board: para cada Match "em jogo", a COLUNA EFETIVA -- que pode ser a etapa
//    atual (arrastável) ou uma coluna terminal DERIVADA do estado real do match
//    ("Contratado"/"Reprovado"), nunca uma posição arrastável.
//
// Regra de ouro do moveCard: reposiciona o card e grava PipelineStageHistory. NUNCA toca em
// StatusMatch / RejectionFeedback / MatchConfirmation -- essas transições continuam sendo
// exclusivas dos fluxos que já existem em MatchService/ProposalService/MatchStatusCheckService.
@Service
public class PipelineService {

    static final List<String> DEFAULT_STAGE_NAMES =
            List.of("Triagem", "Teste", "Entrevista", "Dinâmica", "Proposta");

    private static final String KIND_STAGE = "STAGE";
    private static final String KIND_HIRED = "HIRED";
    private static final String KIND_REJECTED = "REJECTED";

    @Autowired
    private PipelineStageRepository pipelineStageRepository;

    @Autowired
    private PipelineStageHistoryRepository pipelineStageHistoryRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private CompanyMemberRepository companyMemberRepository;

    @Autowired
    private RejectionFeedbackRepository rejectionFeedbackRepository;

    @Autowired
    private MatchConfirmationRepository matchConfirmationRepository;

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

    @Autowired
    private CandidateEvaluationService candidateEvaluationService;

    // ─── Etapas (colunas intermediárias) ─────────────────────────────

    // Todas as etapas da vaga na ordem do board (ativas e arquivadas). Semeia as 5 default se a
    // vaga ainda não tem NENHUMA PipelineStage -- não re-semeia se a empresa arquivou todas.
    @Transactional
    public List<PipelineStage> ensureStages(Project project) {
        if (pipelineStageRepository.countByProjectId(project.getId()) == 0) {
            List<PipelineStage> seeded = new ArrayList<>();
            for (int i = 0; i < DEFAULT_STAGE_NAMES.size(); i++) {
                PipelineStage stage = new PipelineStage();
                stage.setProject(project);
                stage.setName(DEFAULT_STAGE_NAMES.get(i));
                stage.setOrderIndex(i);
                stage.setActive(true);
                seeded.add(stage);
            }
            pipelineStageRepository.saveAll(seeded);
        }
        return pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());
    }

    @Transactional
    public List<PipelineStageDTO> getStages(Long projectId, Long companyId) {
        Project project = requireOwnedProject(projectId, companyId);
        return ensureStages(project).stream().map(this::toStageDTO).toList();
    }

    // Substitui a lista inteira de etapas -- molde de mergeStages: casa por id (edita no lugar),
    // cria as sem id, reordena pela posição na lista, e as omitidas viram active=false (se têm
    // card parado nelas ou já aparecem na trilha) ou são apagadas de verdade (nunca usadas).
    @Transactional
    public List<PipelineStageDTO> replaceStages(
            Long projectId, Long companyId, List<PipelineStageRequestDTO> requested) {
        Project project = requireOwnedProject(projectId, companyId);

        if (requested == null || requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A pipeline must have at least one stage.");
        }

        List<PipelineStage> existing =
                pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());
        Map<Long, PipelineStage> existingById = new HashMap<>();
        for (PipelineStage s : existing) {
            existingById.put(s.getId(), s);
        }

        Set<Long> keptIds = new HashSet<>();
        List<PipelineStage> toSave = new ArrayList<>();

        for (int i = 0; i < requested.size(); i++) {
            PipelineStageRequestDTO req = requested.get(i);
            PipelineStage stage;
            if (req.id() != null) {
                stage = existingById.get(req.id());
                if (stage == null) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "Stage " + req.id() + " does not belong to this project's pipeline.");
                }
                keptIds.add(stage.getId());
            } else {
                stage = new PipelineStage();
                stage.setProject(project);
            }

            if (req.name() == null || req.name().isBlank()) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Stage 'name' is required.");
            }

            stage.setName(req.name().trim());
            stage.setOrderIndex(i);
            stage.setActive(true);
            toSave.add(stage);
        }
        pipelineStageRepository.saveAll(toSave);

        for (PipelineStage old : existing) {
            if (old.getId() != null && !keptIds.contains(old.getId())) {
                boolean inUse = matchRepository.countByPipelineStageId(old.getId()) > 0
                        || pipelineStageHistoryRepository.existsByStageReferenced(old.getId());
                if (inUse) {
                    old.setActive(false);
                    pipelineStageRepository.save(old);
                } else {
                    pipelineStageRepository.delete(old);
                }
            }
        }

        return pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(project.getId())
                .stream().map(this::toStageDTO).toList();
    }

    // ─── Auto-entrada no board (chamado por MatchService) ────────────

    // Chamado nos pontos em que o match vira COMPANY_INTERESTED/PROFESSIONAL_INTERESTED. Se o card
    // ainda não está no board, entra na primeira coluna ativa (semeando as default se a vaga
    // nunca teve board). NÃO grava PipelineStageHistory -- não é movimentação manual e não há um
    // CompanyMember agindo (o gatilho pode ser uma ação do profissional).
    public void assignInitialStageIfAbsent(Match match) {
        if (match.getPipelineStage() != null) {
            return;
        }
        Project project = match.getProject();
        PipelineStage first = pipelineStageRepository
                .findFirstByProjectIdAndActiveTrueOrderByOrderIndexAsc(project.getId())
                .orElse(null);
        if (first == null) {
            ensureStages(project);
            first = pipelineStageRepository
                    .findFirstByProjectIdAndActiveTrueOrderByOrderIndexAsc(project.getId())
                    .orElse(null);
        }
        if (first == null) {
            return; // ensureStages sempre cria 5 ativas -- guarda defensiva
        }
        match.setPipelineStage(first);
        matchRepository.save(match);
    }

    // ─── Board ──────────────────────────────────────────────────────

    @Transactional
    public PipelineBoardDTO getBoard(Long projectId, Long companyId) {
        Project project = requireOwnedProject(projectId, companyId);
        List<PipelineStage> stages = ensureStages(project);

        // Primeiro resolve quais matches entram no board (e em qual coluna) ...
        List<Match> onBoard = new ArrayList<>();
        List<PipelineCardColumnDTO> columns = new ArrayList<>();
        for (Match match : matchRepository.findByProjectId(projectId)) {
            PipelineCardColumnDTO column = deriveColumn(match);
            if (column == null) {
                continue; // pipelineStage == null e não-terminal -> ainda não entrou no board
            }
            onBoard.add(match);
            columns.add(column);
        }

        // ... e agrega o scorecard humano de todos eles numa query só (evita N+1).
        Map<Long, double[]> evalByMatch = candidateEvaluationService.aggregate(
                onBoard.stream().map(Match::getId).toList());

        List<PipelineCardDTO> cards = new ArrayList<>();
        for (int i = 0; i < onBoard.size(); i++) {
            Match match = onBoard.get(i);
            cards.add(toCardDTO(match, columns.get(i), evaluationDTO(evalByMatch.get(match.getId()))));
        }

        return new PipelineBoardDTO(
                projectId,
                stages.stream().map(this::toStageDTO).toList(),
                cards);
    }

    private PipelineCardEvaluationDTO evaluationDTO(double[] agg) {
        if (agg == null || agg[1] <= 0) {
            return new PipelineCardEvaluationDTO(null, 0);
        }
        return new PipelineCardEvaluationDTO(agg[0], (int) agg[1]);
    }

    // Coluna efetiva de um card. Retorna null quando o match não está "em jogo" (sem etapa e não
    // terminal). Deriva as colunas terminais da tupla (status, active, companyStatus,
    // professionalStatus, acceptedProposal, RejectionFeedback, MatchConfirmation, MatchHistory) --
    // ver a tabela do relatório de investigação.
    PipelineCardColumnDTO deriveColumn(Match match) {
        StatusMatch status = match.getStatus();
        boolean active = !Boolean.FALSE.equals(match.getActive());

        if (status == StatusMatch.MATCHED && active) {
            MatchConfirmation confirmation =
                    matchConfirmationRepository.findByMatchId(match.getId()).orElse(null);
            if (confirmation != null && confirmation.getStatus() == MatchConfirmationStatus.CONFIRMED) {
                return terminal(KIND_HIRED, "Confirmada ✓");
            }
            if (match.getAcceptedProposal() != null) {
                return terminal(KIND_HIRED, "Proposta aceita");
            }
            return terminal(KIND_HIRED, "Match confirmado");
        }

        if (status == StatusMatch.MATCHED && !active) {
            boolean hasFeedback =
                    rejectionFeedbackRepository.findByMatchId(match.getId()).isPresent();
            if (!hasFeedback) {
                return terminal(KIND_HIRED, "Match expirado — sem confirmação");
            }
            // MATCHED + inativo + com RejectionFeedback: não deveria ocorrer -- cai no ramo REJECTED.
        }

        if (status == StatusMatch.REJECTED || (status == StatusMatch.MATCHED && !active)) {
            RejectionFeedback feedback =
                    rejectionFeedbackRepository.findByMatchId(match.getId()).orElse(null);
            if (feedback != null && feedback.getRejectedBy() == AuthorType.COMPANY) {
                return terminal(KIND_REJECTED, "Recusado pela empresa");
            }
            if (feedback != null && feedback.getRejectedBy() == AuthorType.PROFESSIONAL) {
                return terminal(KIND_REJECTED, "Candidato recusou");
            }
            if (wasSystemClosed(match.getId())) {
                return terminal(KIND_REJECTED, "Vaga encerrada");
            }
            if (match.getProfessionalStatus() == InterestStatus.REJECTED) {
                return terminal(KIND_REJECTED, "Candidato retirou o interesse");
            }
            if (match.getCompanyStatus() == InterestStatus.REJECTED) {
                return terminal(KIND_REJECTED, "Match cancelado");
            }
            return terminal(KIND_REJECTED, "Recusado");
        }

        PipelineStage stage = match.getPipelineStage();
        if (stage == null) {
            return null;
        }
        return new PipelineCardColumnDTO(
                KIND_STAGE,
                stage.getId(),
                Boolean.TRUE.equals(stage.getActive()) ? null : "Etapa arquivada");
    }

    // Encerramento de vaga: cancelPendingMatchesForClosedProject grava MatchHistory com
    // changedBy="SYSTEM" e NÃO marca companyStatus/professionalStatus (é o que distingue de uma
    // recusa ativa).
    private boolean wasSystemClosed(Long matchId) {
        return matchHistoryRepository.findByMatchIdOrderByChangedAtAsc(matchId).stream()
                .anyMatch(h -> "SYSTEM".equals(h.getChangedBy())
                        && "REJECTED".equals(h.getToStatus()));
    }

    private PipelineCardColumnDTO terminal(String kind, String subLabel) {
        return new PipelineCardColumnDTO(kind, null, subLabel);
    }

    // ─── Mover card entre etapas intermediárias ─────────────────────

    @Transactional
    public PipelineCardDTO moveCard(
            Long projectId, Long matchId, Long stageId, Long companyId, Long actingUserId) {
        requireOwnedProject(projectId, companyId);

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + matchId));
        if (!match.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match does not belong to project " + projectId + ".");
        }

        if (stageId == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "'stageId' is required.");
        }
        PipelineStage target = pipelineStageRepository.findById(stageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(400), "Pipeline stage not found: " + stageId));
        if (!target.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This pipeline stage belongs to another project.");
        }
        if (!Boolean.TRUE.equals(target.getActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This pipeline stage is archived and cannot receive cards.");
        }

        PipelineCardColumnDTO current = deriveColumn(match);
        if (current == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This card is not on the board yet.");
        }
        if (!KIND_STAGE.equals(current.kind())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This card is in a terminal column and cannot be moved.");
        }

        PipelineStage from = match.getPipelineStage();
        if (from != null && from.getId().equals(stageId)) {
            return toCardDTO(match, deriveColumn(match), evaluationFor(match.getId())); // no-op: sem trilha
        }

        CompanyMember movedBy = companyMemberRepository
                .findByUserIdAndStatus(actingUserId, CompanyMemberStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(403), "Active company membership required."));

        match.setPipelineStage(target);
        matchRepository.save(match);

        PipelineStageHistory row = new PipelineStageHistory();
        row.setMatch(match);
        row.setFromStage(from);
        row.setToStage(target);
        row.setMovedBy(movedBy);
        row.setMovedAt(LocalDateTime.now());
        pipelineStageHistoryRepository.save(row);

        return toCardDTO(match, deriveColumn(match), evaluationFor(match.getId()));
    }

    private PipelineCardEvaluationDTO evaluationFor(Long matchId) {
        return evaluationDTO(candidateEvaluationService.aggregate(List.of(matchId)).get(matchId));
    }

    // ─── Trilha (COMPANY-only) ──────────────────────────────────────

    @Transactional
    public List<PipelineStageHistoryDTO> getCardHistory(
            Long projectId, Long matchId, Long companyId) {
        requireOwnedProject(projectId, companyId);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + matchId));
        if (!match.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match does not belong to project " + projectId + ".");
        }
        return pipelineStageHistoryRepository.findByMatchIdOrderByMovedAtAsc(matchId)
                .stream().map(this::toHistoryDTO).toList();
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private Project requireOwnedProject(Long projectId, Long companyId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + projectId));
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }
        return project;
    }

    private PipelineCardDTO toCardDTO(
            Match match, PipelineCardColumnDTO column, PipelineCardEvaluationDTO evaluation) {
        Professional professional = match.getProfessional();
        ScreeningInvitationSummaryDTO latestScreening = screeningInvitationService
                .getSummariesFor(match.getProject(), professional)
                .stream()
                .findFirst()
                .orElse(null);

        return new PipelineCardDTO(
                match.getId(),
                new ProfessionalSummaryDTO(
                        professional.getId(),
                        professional.getName(),
                        professional.getPhone(),
                        professional.getReputation(),
                        professional.getProfilePhotoUrl(),
                        professional.getSkills().stream().map(Skill::getName).toList()),
                match.getMatchScore(),          // score algorítmico -- campo próprio
                evaluation,                     // scorecard humano -- objeto separado, nunca misturado
                match.getStatus(),
                match.getActive(),
                KIND_STAGE.equals(column.kind()),
                column,
                latestScreening);
    }

    private PipelineStageDTO toStageDTO(PipelineStage stage) {
        return new PipelineStageDTO(
                stage.getId(), stage.getName(), stage.getOrderIndex(), stage.getActive());
    }

    private PipelineStageHistoryDTO toHistoryDTO(PipelineStageHistory row) {
        PipelineStage from = row.getFromStage();
        User mover = row.getMovedBy() != null ? row.getMovedBy().getUser() : null;
        return new PipelineStageHistoryDTO(
                row.getId(),
                row.getMatch().getId(),
                from != null ? from.getId() : null,
                from != null ? from.getName() : null,
                row.getToStage().getId(),
                row.getToStage().getName(),
                mover != null ? mover.getId() : null,
                mover != null ? mover.getEmail() : null,
                row.getMovedAt());
    }
}
