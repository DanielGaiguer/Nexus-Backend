package com.main.nexus.service;

import com.main.nexus.dto.CompanyDashboardAnalyticsDTO;
import com.main.nexus.dto.MatchSummaryDTO;
import com.main.nexus.dto.MonthlyMatchDTO;
import com.main.nexus.dto.ProjectAcceptanceRateDTO;
import com.main.nexus.dto.ReputationSummaryDTO;
import com.main.nexus.dto.ScoreDistributionDTO;
import com.main.nexus.dto.SkillDemandDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Project;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyAnalyticsService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    // ── Ponto de entrada principal ────────────────────────────────────────────

    public CompanyDashboardAnalyticsDTO buildDashboard(Long companyId) {
        return new CompanyDashboardAnalyticsDTO(
                buildMatchSummary(companyId),
                buildMonthlyMatches(companyId),
                buildScoreDistribution(companyId),
                buildAcceptanceRatePerProject(companyId),
                buildMostRequiredSkills(companyId),
                buildReputationSummary(companyId)
        );
    }

    // ── 1. Resumo geral de matches ────────────────────────────────────────────

    private MatchSummaryDTO buildMatchSummary(Long companyId) {
        long total     = matchRepository.countByCompanyId(companyId);
        long confirmed = matchRepository.countByCompanyIdAndStatus(companyId, StatusMatch.MATCHED);
        long rejected  = matchRepository.countByCompanyIdAndStatus(companyId, StatusMatch.REJECTED);
        long pending   = total - confirmed - rejected;

        double acceptanceRate = total > 0
                ? Math.round((double) confirmed / total * 1000.0) / 10.0
                : 0.0;

        Double avgScore = matchRepository.findAverageMatchScoreByCompany(companyId);
        double roundedAvg = avgScore != null
                ? Math.round(avgScore * 10.0) / 10.0
                : 0.0;

        return new MatchSummaryDTO(
                total,
                confirmed,
                pending,
                rejected,
                acceptanceRate,
                roundedAvg
        );
    }

    // ── 2. Matches por mês (últimos 12 meses) ────────────────────────────────

    private List<MonthlyMatchDTO> buildMonthlyMatches(Long companyId) {
        LocalDateTime since = LocalDateTime.now().minusMonths(12);
        List<Object[]> raw = matchRepository.findMonthlyMatchStatsByCompany(companyId, since);

        // Monta um mapa [year][month] → {confirmed, rejected, total}
        Map<String, long[]> aggregated = new LinkedHashMap<>();

        for (Object[] row : raw) {
            int year   = ((Number) row[0]).intValue();
            int month  = ((Number) row[1]).intValue();
            StatusMatch status = StatusMatch.valueOf(row[2].toString());
            long count = ((Number) row[3]).longValue();

            String key = year + "-" + String.format("%02d", month);
            aggregated.putIfAbsent(key, new long[]{year, month, 0, 0, 0});

            long[] data = aggregated.get(key);
            data[2] += count; // total
            if (status == StatusMatch.MATCHED)  data[3] += count; // confirmed
            if (status == StatusMatch.REJECTED) data[4] += count; // rejected
        }

        List<MonthlyMatchDTO> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : aggregated.entrySet()) {
            long[] data = entry.getValue();
            int year  = (int) data[0];
            int month = (int) data[1];
            String label = Month.of(month)
                    .getDisplayName(TextStyle.SHORT, new Locale("pt", "BR"))
                    + "/" + year;

            result.add(new MonthlyMatchDTO(year, month, label, data[2], data[3], data[4]));
        }

        return result;
    }

    // ── 3. Distribuição de scores em faixas ──────────────────────────────────

    private List<ScoreDistributionDTO> buildScoreDistribution(Long companyId) {
        List<Double> scores = matchRepository.findAllScoresByCompany(companyId);

        if (scores.isEmpty()) return List.of();

        long[] buckets = new long[5]; // 0-20, 20-40, 40-60, 60-80, 80-100
        for (Double score : scores) {
            if (score == null) continue;
            int idx = Math.min((int) (score / 20), 4);
            buckets[idx]++;
        }

        String[] labels = {"0–20", "20–40", "40–60", "60–80", "80–100"};
        List<ScoreDistributionDTO> result = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            double percentage = scores.size() > 0
                    ? Math.round((double) buckets[i] / scores.size() * 1000.0) / 10.0
                    : 0.0;
            result.add(new ScoreDistributionDTO(labels[i], buckets[i], percentage));
        }

        return result;
    }

    // ── 4. Taxa de aceitação por projeto ─────────────────────────────────────

    private List<ProjectAcceptanceRateDTO> buildAcceptanceRatePerProject(Long companyId) {
        List<Project> projects = projectRepository.findByCompanyId(companyId);
        List<ProjectAcceptanceRateDTO> result = new ArrayList<>();

        for (Project project : projects) {
            List<Match> matches = matchRepository.findByProjectIdOrderByScore(project.getId());

            if (matches.isEmpty()) continue;

            long confirmed = matches.stream()
                    .filter(m -> m.getStatus() == StatusMatch.MATCHED).count();
            long rejected  = matches.stream()
                    .filter(m -> m.getStatus() == StatusMatch.REJECTED).count();

            double acceptanceRate = matches.size() > 0
                    ? Math.round((double) confirmed / matches.size() * 1000.0) / 10.0
                    : 0.0;

            double avgScore = matches.stream()
                    .mapToDouble(Match::getMatchScore)
                    .average()
                    .orElse(0.0);
            avgScore = Math.round(avgScore * 10.0) / 10.0;

            result.add(new ProjectAcceptanceRateDTO(
                    project.getId(),
                    project.getTitle(),
                    matches.size(),
                    confirmed,
                    rejected,
                    acceptanceRate,
                    avgScore
            ));
        }

        return result;
    }

    // ── 5. Skills mais exigidas nos projetos da empresa ──────────────────────

    private List<SkillDemandDTO> buildMostRequiredSkills(Long companyId) {
        List<Object[]> raw = projectRepository.findMostRequiredSkillsByCompany(companyId);
        List<SkillDemandDTO> result = new ArrayList<>();

        for (Object[] row : raw) {
            String skillName = (String) row[0];
            String category  = (String) row[1];
            long count       = ((Number) row[2]).longValue();
            result.add(new SkillDemandDTO(skillName, category, count));
        }

        return result;
    }

    // ── 6. Resumo de reputação da empresa ────────────────────────────────────

    private ReputationSummaryDTO buildReputationSummary(Long companyId) {
        Optional<ReputationMetrics> metricsOpt =
                reputationMetricsRepository.findByCompanyId(companyId);

        if (metricsOpt.isEmpty()) {
            return new ReputationSummaryDTO(
                    0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        ReputationMetrics m = metricsOpt.get();

        return new ReputationSummaryDTO(
                m.getReputationScore(),
                m.getConfidenceScore() != null ? m.getConfidenceScore() * 100 : 0.0,
                m.getTotalReviews(),
                m.getSatisfactionAverage(),
                m.getRecommendationRate(),
                m.getCommunication(),
                m.getReliability(),
                m.getPunctuality(),
                m.getProfessionalism()
        );
    }
}