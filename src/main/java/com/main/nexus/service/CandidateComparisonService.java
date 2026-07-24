package com.main.nexus.service;

import com.main.nexus.dto.CandidateComparisonItemDTO;
import com.main.nexus.dto.CandidateComparisonRequestDTO;
import com.main.nexus.dto.CandidateComparisonResponseDTO;
import com.main.nexus.dto.ScoreBreakdownDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CandidateComparisonService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    @Autowired
    private MatchService matchService;

    private static final int MAX_CANDIDATES = 5;

    public CandidateComparisonResponseDTO compare(
            CandidateComparisonRequestDTO request, Long companyId) {

        if (request.matchIds() == null || request.matchIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "At least one match ID is required.");
        }

        if (request.matchIds().size() > MAX_CANDIDATES) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Maximum of " + MAX_CANDIDATES + " candidates can be compared at once.");
        }

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found."));

        // Valida que o projeto pertence à empresa autenticada
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }

        List<String> requiredSkillNames = project.getRequiredSkills()
                .stream().map(Skill::getName).toList();

        List<CandidateComparisonItemDTO> candidates = new ArrayList<>();

        for (Long matchId : request.matchIds()) {
            Match match = matchRepository.findById(matchId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(404), "Match not found: " + matchId));

            // Garante que o match pertence ao projeto informado
            if (!match.getProject().getId().equals(project.getId())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Match " + matchId + " does not belong to project " + project.getId() + ".");
            }

            Professional professional = match.getProfessional();
            candidates.add(buildComparisonItem(match, professional, project, requiredSkillNames));
        }

        return new CandidateComparisonResponseDTO(
                project.getId(),
                project.getTitle(),
                requiredSkillNames,
                project.getWorkMode() != null ? project.getWorkMode().name() : null,
                project.getExperienceLevel() != null ? project.getExperienceLevel().name() : null,
                project.getMinimumBudget(),
                project.getMaximumBudget(),
                candidates
        );
    }

    // ── Monta o item de comparação de um candidato ────────────────────────────

    private CandidateComparisonItemDTO buildComparisonItem(
            Match match,
            Professional professional,
            Project project,
            List<String> requiredSkillNames) {

        List<String> professionalSkillNames = professional.getSkills()
                .stream().map(Skill::getName).toList();

        List<String> matchingSkills = requiredSkillNames.stream()
                .filter(s -> professionalSkillNames.stream()
                        .anyMatch(ps -> ps.equalsIgnoreCase(s)))
                .toList();

        List<String> missingSkills = requiredSkillNames.stream()
                .filter(s -> professionalSkillNames.stream()
                        .noneMatch(ps -> ps.equalsIgnoreCase(s)))
                .toList();

        ReputationMetrics metrics = reputationMetricsRepository
                .findByProfessionalId(professional.getId())
                .orElse(null);

        ScoreBreakdownDTO breakdown = buildScoreBreakdown(professional, project, match);
        Double expectedSalary = matchService.calculateExpectedSalary(professional, project);

        return new CandidateComparisonItemDTO(
                match.getId(),
                professional.getId(),
                professional.getName(),
                professional.getCity(),
                professional.getUf(),
                professional.getProfilePhotoUrl(),
                professional.getExperienceLevel(),
                professional.getReputation(),
                metrics != null ? metrics.getConfidenceScore() != null
                        ? metrics.getConfidenceScore() * 100 : 0.0 : 0.0,
                metrics != null ? metrics.getTotalReviews() : 0,
                expectedSalary,
                expectedSalary,
                professionalSkillNames,
                matchingSkills,
                missingSkills,
                professional.getProjects() != null ? professional.getProjects().size() : 0,
                professional.getAvailable(),
                match.getStatus(),
                breakdown
        );
    }

    // ── Reconstrói o breakdown de score componente a componente ──────────────

    private ScoreBreakdownDTO buildScoreBreakdown(
            Professional professional, Project project, Match match) {

        double skillScore        = matchService.calculateSkillScore(professional, project);
        double budgetScore       = matchService.calculateBudgetScore(professional, project);
        double historyScore      = matchService.calculateHistoryScore(professional);
        double reputationScore   = matchService.calculateReputationScore(professional);
        double availabilityScore = matchService.calculateAvailabilityScore(professional);

        boolean considersDistance   = project.getWorkMode() == Modality.ONSITE
                                    || project.getWorkMode() == Modality.HYBRID;
        boolean considersExperience = project.getExperienceLevel() != null;

        Double distanceScore   = considersDistance
                ? matchService.calculateDistanceScore(professional, project) : null;
        Double experienceScore = considersExperience
                ? matchService.calculateExperienceScore(professional, project) : null;

        double baseScore = computeBaseScore(
                skillScore, budgetScore, historyScore, reputationScore,
                availabilityScore, distanceScore, experienceScore,
                considersDistance, considersExperience);

        double reputationAdjustment = match.getMatchScore() - baseScore;

        return new ScoreBreakdownDTO(
                round(skillScore),
                round(budgetScore),
                round(historyScore),
                round(reputationScore),
                round(availabilityScore),
                distanceScore   != null ? round(distanceScore)   : null,
                experienceScore != null ? round(experienceScore) : null,
                round(reputationAdjustment),
                round(match.getMatchScore())
        );
    }

    private double computeBaseScore(
            double skill, double budget, double history,
            double reputation, double availability,
            Double distance, Double experience,
            boolean considersDistance, boolean considersExperience) {

        if (considersDistance && considersExperience) {
            return (skill * 0.26) + (budget * 0.18) + (history * 0.15)
                 + (reputation * 0.08) + (availability * 0.08)
                 + (distance   * 0.13) + (experience  * 0.12);
        } else if (considersDistance) {
            return (skill * 0.30) + (budget * 0.20) + (history * 0.17)
                 + (reputation * 0.09) + (availability * 0.09)
                 + (distance * 0.15);
        } else if (considersExperience) {
            return (skill * 0.30) + (budget * 0.22) + (history * 0.18)
                 + (reputation * 0.09) + (availability * 0.09)
                 + (experience * 0.12);
        } else {
            return (skill * 0.35) + (budget * 0.25) + (history * 0.20)
                 + (reputation * 0.10) + (availability * 0.10);
        }
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}