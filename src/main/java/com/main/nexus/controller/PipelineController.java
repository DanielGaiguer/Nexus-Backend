package com.main.nexus.controller;

import com.main.nexus.dto.CandidateEvaluationItemDTO;
import com.main.nexus.dto.CandidateEvaluationRequestDTO;
import com.main.nexus.dto.CandidateEvaluationSummaryDTO;
import com.main.nexus.dto.MovePipelineCardRequestDTO;
import com.main.nexus.dto.PipelineBoardDTO;
import com.main.nexus.dto.PipelineCardDTO;
import com.main.nexus.dto.PipelineStageDTO;
import com.main.nexus.dto.PipelineStageHistoryDTO;
import com.main.nexus.dto.PipelineStagesRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.CandidateEvaluationService;
import com.main.nexus.service.CompanyAccessService;
import com.main.nexus.service.PipelineService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Kanban de contratação de uma vaga. Fica sob /api/projects/** -> hasRole("COMPANY") no
// SecurityConfig: um profissional autenticado nem chega aqui (403 na cadeia de filtros). Sem
// requireOwner -- recrutamento é livre para MEMBER (mesma régua de MatchController/ProjectController,
// que só chamam companyAccessService.resolve).
//
// A trilha de movimentação (/matches/{matchId}/history) fica AQUI de propósito, e nunca no
// MatchController (que os dois lados usam) -- ver PipelineStageHistory.
@RestController
@RequestMapping("/api/projects/{projectId}/pipeline")
public class PipelineController {

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private CandidateEvaluationService candidateEvaluationService;

    @Autowired
    private CompanyAccessService companyAccessService;

    // Board pronto para renderizar: colunas intermediárias + cards com a coluna efetiva já
    // resolvida (etapa atual ou coluna terminal derivada). Semeia as 5 etapas default na primeira
    // leitura de uma vaga sem board.
    @GetMapping
    public ResponseEntity<PipelineBoardDTO> board(@PathVariable Long projectId) {
        return ResponseEntity.ok(pipelineService.getBoard(projectId, loggedCompanyId()));
    }

    @GetMapping("/stages")
    public ResponseEntity<List<PipelineStageDTO>> stages(@PathVariable Long projectId) {
        return ResponseEntity.ok(pipelineService.getStages(projectId, loggedCompanyId()));
    }

    // Substitui a lista inteira de etapas (criar/editar/reordenar/desativar) -- molde de
    // ScreeningQuestionnaireService.mergeStages.
    @PutMapping("/stages")
    public ResponseEntity<List<PipelineStageDTO>> replaceStages(
            @PathVariable Long projectId, @RequestBody PipelineStagesRequestDTO request) {
        return ResponseEntity.ok(pipelineService.replaceStages(
                projectId, loggedCompanyId(), request.stages()));
    }

    // Reposiciona um card entre etapas intermediárias. 400 claro se a etapa de destino não for
    // ativa ou for de outro projeto, ou se o card estiver numa coluna terminal. Nunca altera
    // StatusMatch/RejectionFeedback/MatchConfirmation.
    @PutMapping("/matches/{matchId}/stage")
    public ResponseEntity<PipelineCardDTO> moveCard(
            @PathVariable Long projectId,
            @PathVariable Long matchId,
            @RequestBody MovePipelineCardRequestDTO request) {
        UserDTO logged = getLoggedUser();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        return ResponseEntity.ok(pipelineService.moveCard(
                projectId, matchId, request.stageId(), access.company().getId(), logged.id()));
    }

    // Trilha de movimentação de um card -- COMPANY-only (trilha separada de MatchHistory).
    @GetMapping("/matches/{matchId}/history")
    public ResponseEntity<List<PipelineStageHistoryDTO>> cardHistory(
            @PathVariable Long projectId, @PathVariable Long matchId) {
        return ResponseEntity.ok(
                pipelineService.getCardHistory(projectId, matchId, loggedCompanyId()));
    }

    // ─── Scorecard colaborativo (Passo 3) ──────────────────────────────

    // Consolidado: { average (ao vivo, 1-5), count, items[] }. Distinto do matchScore algorítmico.
    @GetMapping("/matches/{matchId}/evaluations")
    public ResponseEntity<CandidateEvaluationSummaryDTO> evaluations(
            @PathVariable Long projectId, @PathVariable Long matchId) {
        UserDTO logged = getLoggedUser();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        return ResponseEntity.ok(candidateEvaluationService.getSummary(
                projectId, matchId, access.company().getId(), logged.id()));
    }

    // Upsert do parecer do avaliador logado ({ rating 1-5, comment? }).
    @PutMapping("/matches/{matchId}/evaluations/mine")
    public ResponseEntity<CandidateEvaluationItemDTO> upsertMyEvaluation(
            @PathVariable Long projectId,
            @PathVariable Long matchId,
            @RequestBody CandidateEvaluationRequestDTO body) {
        UserDTO logged = getLoggedUser();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        return ResponseEntity.ok(candidateEvaluationService.upsertMine(
                projectId, matchId, access.company().getId(), logged.id(), body));
    }

    // ─── Identidade (mesmo padrão de ProjectController/MatchController) ──

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private Long loggedCompanyId() {
        return companyAccessService.resolve(getLoggedUser()).company().getId();
    }
}
