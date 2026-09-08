package com.main.nexus.service;

import com.main.nexus.dto.CandidateEvaluationItemDTO;
import com.main.nexus.dto.CandidateEvaluationRequestDTO;
import com.main.nexus.dto.CandidateEvaluationSummaryDTO;
import com.main.nexus.model.CandidateEvaluation;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.Match;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.repository.CandidateEvaluationRepository;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProjectRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Scorecard colaborativo do Kanban de contratação (Passo 3). Cada CompanyMember ACTIVE dá UM
// parecer (rating 1-5 + comentário) por candidato/processo (Match); o consolidado é a média ao
// vivo + a lista de pareceres.
//
// COMPANY-only (endpoints sob /api/projects/** = hasRole("COMPANY")), sem requireOwner -- é
// operação de recrutamento. A média nunca é persistida (mesmo padrão de
// MatchService.getScoreBreakdown). É DISTINTA do Match.matchScore algorítmico -- ver
// PipelineCardDTO.
@Service
public class CandidateEvaluationService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    @Autowired
    private CandidateEvaluationRepository evaluationRepository;

    @Autowired
    private CompanyMemberRepository companyMemberRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ProjectRepository projectRepository;

    // ─── Consolidado ────────────────────────────────────────────────

    @Transactional
    public CandidateEvaluationSummaryDTO getSummary(
            Long projectId, Long matchId, Long companyId, Long viewerUserId) {

        requireOwnedProjectAndMatch(projectId, matchId, companyId);
        Long viewerMemberId = companyMemberRepository
                .findByUserIdAndStatus(viewerUserId, CompanyMemberStatus.ACTIVE)
                .map(CompanyMember::getId)
                .orElse(null);

        List<CandidateEvaluation> evaluations =
                evaluationRepository.findByMatchIdOrderByCreatedAtAsc(matchId);

        Double average = evaluations.isEmpty() ? null : roundToOneDecimal(
                evaluations.stream().mapToInt(CandidateEvaluation::getRating).average().orElse(0));

        List<CandidateEvaluationItemDTO> items = evaluations.stream()
                .map(e -> toItemDTO(e, viewerMemberId))
                .toList();

        return new CandidateEvaluationSummaryDTO(average, evaluations.size(), items);
    }

    // ─── Upsert do parecer do avaliador logado ──────────────────────

    @Transactional
    public CandidateEvaluationItemDTO upsertMine(
            Long projectId, Long matchId, Long companyId, Long viewerUserId,
            CandidateEvaluationRequestDTO request) {

        Match match = requireOwnedProjectAndMatch(projectId, matchId, companyId);
        CompanyMember evaluator = companyMemberRepository
                .findByUserIdAndStatus(viewerUserId, CompanyMemberStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(403), "Active company membership required."));

        int rating = requireRating(request != null ? request.rating() : null);
        String comment = normalizeComment(request != null ? request.comment() : null);
        LocalDateTime now = LocalDateTime.now();

        CandidateEvaluation evaluation = evaluationRepository
                .findByMatchIdAndEvaluatorId(matchId, evaluator.getId())
                .orElseGet(() -> {
                    CandidateEvaluation fresh = new CandidateEvaluation();
                    fresh.setMatch(match);
                    fresh.setEvaluator(evaluator);
                    // Snapshot da identidade -- só na criação, nunca relido depois.
                    fresh.setEvaluatorLabel(labelFor(evaluator));
                    fresh.setCreatedAt(now);
                    return fresh;
                });

        evaluation.setRating(rating);
        evaluation.setComment(comment);
        evaluation.setUpdatedAt(now);

        return toItemDTO(evaluationRepository.save(evaluation), evaluator.getId());
    }

    // ─── Agregação para o board (usada por PipelineService) ─────────

    // matchId -> [média 1 casa, contagem]. Só entram matches com ao menos um parecer; o resto o
    // chamador trata como (null, 0).
    @Transactional
    public Map<Long, double[]> aggregate(Collection<Long> matchIds) {
        Map<Long, double[]> out = new HashMap<>();
        if (matchIds == null || matchIds.isEmpty()) {
            return out;
        }
        for (Object[] row : evaluationRepository.aggregateByMatchIds(matchIds)) {
            Long matchId = ((Number) row[0]).longValue();
            double avg = row[1] != null ? roundToOneDecimal(((Number) row[1]).doubleValue()) : 0.0;
            double count = ((Number) row[2]).doubleValue();
            out.put(matchId, new double[] {avg, count});
        }
        return out;
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private Match requireOwnedProjectAndMatch(Long projectId, Long matchId, Long companyId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + projectId));
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + matchId));
        if (!match.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match does not belong to project " + projectId + ".");
        }
        return match;
    }

    private int requireRating(Integer rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "'rating' must be an integer from " + MIN_RATING + " to " + MAX_RATING + ".");
        }
        return rating;
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String labelFor(CompanyMember member) {
        User user = member.getUser();
        return user != null && user.getEmail() != null ? user.getEmail() : "membro #" + member.getId();
    }

    private CandidateEvaluationItemDTO toItemDTO(CandidateEvaluation evaluation, Long viewerMemberId) {
        CompanyMember evaluator = evaluation.getEvaluator();
        boolean mine = evaluator != null && evaluator.getId() != null
                && evaluator.getId().equals(viewerMemberId);
        return new CandidateEvaluationItemDTO(
                evaluator != null ? evaluator.getId() : null,
                evaluator != null && evaluator.getUser() != null ? evaluator.getUser().getId() : null,
                evaluation.getEvaluatorLabel(),
                evaluation.getRating(),
                evaluation.getComment(),
                mine,
                evaluation.getCreatedAt(),
                evaluation.getUpdatedAt());
    }
}
