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
import com.main.nexus.model.enums.OpportunityType;
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

    private static final int MIN_CANDIDATES = 2;
    private static final int MAX_CANDIDATES = 5;

    public CandidateComparisonResponseDTO compare(
            CandidateComparisonRequestDTO request, Long companyId) {

        if (request.matchIds() == null || request.matchIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "At least one match ID is required.");
        }

        if (request.matchIds().size() < MIN_CANDIDATES) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "At least " + MIN_CANDIDATES + " candidates are required to compare.");
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
                project.getOpportunityType() != null ? project.getOpportunityType().name() : null,
                project.getMinimumBudget(),
                project.getMaximumBudget(),
                project.getMonthlySalaryMin(),
                project.getMonthlySalaryMax(),
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

        ScoreBreakdownDTO breakdown = matchService.getScoreBreakdown(professional, project, match);
        SalaryRange salaryRange = resolveSalaryRange(professional, project);

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
                salaryRange.min(),
                salaryRange.max(),
                professionalSkillNames,
                matchingSkills,
                missingSkills,
                professional.getProjects() != null ? professional.getProjects().size() : 0,
                professional.getAvailable(),
                match.getStatus(),
                breakdown
        );
    }

    // ── Faixa de pretensão salarial relevante para o regime da vaga/projeto ──
    // CLT/PJ são valores únicos (min = max); freelance/temporário e PROJECT
    // usam a faixa min/max informada pelo profissional.

    private record SalaryRange(Double min, Double max) {}

    private SalaryRange resolveSalaryRange(Professional professional, Project project) {
        if (project.getOpportunityType() == OpportunityType.JOB && project.getContractType() != null) {
            return switch (project.getContractType()) {
                case CLT, INTERNSHIP -> new SalaryRange(
                        professional.getExpectedSalaryCLT(), professional.getExpectedSalaryCLT());
                case PJ -> new SalaryRange(
                        professional.getExpectedSalaryPJ(), professional.getExpectedSalaryPJ());
                case TEMPORARY, FREELANCER -> new SalaryRange(
                        professional.getFreelanceMinExpectation(), professional.getFreelanceMaxExpectation());
            };
        }
        return new SalaryRange(
                professional.getFreelanceMinExpectation(), professional.getFreelanceMaxExpectation());
    }

}