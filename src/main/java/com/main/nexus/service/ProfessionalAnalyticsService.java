package com.main.nexus.service;

import com.main.nexus.dto.CompanyAcceptanceRateDTO;
import com.main.nexus.dto.MatchSummaryDTO;
import com.main.nexus.dto.MonthlyMatchDTO;
import com.main.nexus.dto.ProfessionalDashboardAnalyticsDTO;
import com.main.nexus.dto.ReputationSummaryDTO;
import com.main.nexus.dto.ScoreDistributionDTO;
import com.main.nexus.dto.SkillDemandDTO;
import com.main.nexus.dto.SkillGapDTO;
import com.main.nexus.dto.SoftSkillFeedbackDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.Review;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.NegativeReason;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ReviewRepository;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProfessionalAnalyticsService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ReputationService reputationService;

    // Ponto de entrada principal

    public ProfessionalDashboardAnalyticsDTO buildDashboard(Long professionalId) {
        return new ProfessionalDashboardAnalyticsDTO(
                buildMatchSummary(professionalId),
                buildMonthlyMatches(professionalId),
                buildScoreDistribution(professionalId),
                buildAcceptanceRatePerCompany(professionalId),
                buildMostRequiredSkills(professionalId),
                buildReputationSummary(professionalId),
                buildSkillGaps(professionalId),
                buildSoftSkillFeedback(professionalId)
        );
    }

    // Skills que aparecem como requisito nos projetos/vagas em que o profissional teve
    // algum match real (WAITING puro fora, mesmo critério do buildAcceptanceRatePerCompany)
    // mas que não estão no perfil dele — indicação de aprendizado pro card "HardSkills".
    private List<SkillGapDTO> buildSkillGaps(Long professionalId) {
        Professional professional = professionalRepository.findById(professionalId).orElse(null);
        if (professional == null) return List.of();

        Set<String> mySkills = professional.getSkills().stream()
                .map(s -> s.getName().toLowerCase())
                .collect(Collectors.toSet());

        List<Match> matches = matchRepository.findByProfessionalId(professionalId).stream()
                .filter(m -> m.getStatus() != StatusMatch.WAITING)
                .toList();

        Map<String, Long> countByName = new LinkedHashMap<>();
        Map<String, String> categoryByName = new LinkedHashMap<>();

        for (Match m : matches) {
            for (Skill skill : m.getProject().getRequiredSkills()) {
                if (mySkills.contains(skill.getName().toLowerCase())) continue;
                countByName.merge(skill.getName(), 1L, Long::sum);
                categoryByName.putIfAbsent(skill.getName(), skill.getCategory());
            }
        }

        return countByName.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(e -> new SkillGapDTO(e.getKey(), categoryByName.get(e.getKey()), e.getValue()))
                .toList();
    }

    // Motivos negativos mais frequentes nas avaliações que o profissional recebeu das
    // empresas — indicação de aprimoramento pro card "SoftSkills".
    private List<SoftSkillFeedbackDTO> buildSoftSkillFeedback(Long professionalId) {
        List<Review> reviews = reviewRepository.findCompanyReviewsForProfessional(professionalId);

        Map<NegativeReason, Long> counts = new EnumMap<>(NegativeReason.class);
        for (Review review : reviews) {
            if (review.getNegativeReasons() == null) continue;
            for (NegativeReason reason : review.getNegativeReasons()) {
                counts.merge(reason, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<NegativeReason, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> SoftSkillFeedbackDTO.of(e.getKey(), e.getValue()))
                .toList();
    }

    // Resumo geral de matches

    private MatchSummaryDTO buildMatchSummary(Long professionalId) {
        // waiting e apenas o candidato pontuado automaticamente pelo ranking oi oportunidades
        // nunca virou um convite  e interesse real, então não deve contar como match
        // nem inflar os pendentes ver MatchService.getOpportunitiesForProfessional.
        long companyInterested      = matchRepository.countByProfessionalIdAndStatus(professionalId, StatusMatch.COMPANY_INTERESTED);
        long professionalInterested = matchRepository.countByProfessionalIdAndStatus(professionalId, StatusMatch.PROFESSIONAL_INTERESTED);
        long confirmed = matchRepository.countByProfessionalIdAndStatus(professionalId, StatusMatch.MATCHED);
        long rejected  = matchRepository.countByProfessionalIdAndStatus(professionalId, StatusMatch.REJECTED);
        long pending   = companyInterested + professionalInterested;
        long total     = pending + confirmed + rejected;

        double acceptanceRate = total > 0
                ? Math.round((double) confirmed / total * 1000.0) / 10.0
                : 0.0;

        return new MatchSummaryDTO(
                total,
                confirmed,
                pending,
                rejected,
                acceptanceRate
        );
    }

    // Matches por mês ultimos 12 meses

    private List<MonthlyMatchDTO> buildMonthlyMatches(Long professionalId) {
        LocalDateTime since = LocalDateTime.now().minusMonths(12);
        List<Object[]> raw = matchRepository.findMonthlyMatchStatsByProfessional(professionalId, since);

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

    // Distribuição de scores em faixa 

    private List<ScoreDistributionDTO> buildScoreDistribution(Long professionalId) {
        List<Double> scores = matchRepository.findAllScoresByProfessional(professionalId);

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

    // Taxa de aceitação por empresa
    // Análogo profissional do por projeto da empresa uma empresa pode ter
    // gerado vários matches com o profissional em projetos diferentes.

    private List<CompanyAcceptanceRateDTO> buildAcceptanceRatePerCompany(Long professionalId) {
        // Exclui candidatos WAITING (nunca contatados) — mesma razão do buildMatchSummary()
        List<Match> matches = matchRepository.findByProfessionalId(professionalId).stream()
                .filter(m -> m.getStatus() != StatusMatch.WAITING)
                .toList();

        Map<Long, List<Match>> byCompany = new LinkedHashMap<>();
        Map<Long, String> companyNames = new LinkedHashMap<>();

        for (Match m : matches) {
            Company company = m.getProject().getCompany();
            if (company == null) continue;
            byCompany.computeIfAbsent(company.getId(), k -> new ArrayList<>()).add(m);
            companyNames.putIfAbsent(company.getId(), company.getCompanyName());
        }

        List<CompanyAcceptanceRateDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<Match>> entry : byCompany.entrySet()) {
            List<Match> companyMatches = entry.getValue();

            long confirmed = companyMatches.stream()
                    .filter(m -> m.getStatus() == StatusMatch.MATCHED).count();
            long rejected  = companyMatches.stream()
                    .filter(m -> m.getStatus() == StatusMatch.REJECTED).count();

            double acceptanceRate = companyMatches.size() > 0
                    ? Math.round((double) confirmed / companyMatches.size() * 1000.0) / 10.0
                    : 0.0;

            result.add(new CompanyAcceptanceRateDTO(
                    entry.getKey(),
                    companyNames.get(entry.getKey()),
                    companyMatches.size(),
                    confirmed,
                    rejected,
                    acceptanceRate
            ));
        }

        return result;
    }

    // Skills mais presentes nos projetos em que o profissional deu match

    private List<SkillDemandDTO> buildMostRequiredSkills(Long professionalId) {
        List<Object[]> raw = matchRepository.findMostRequiredSkillsByProfessional(professionalId);
        List<SkillDemandDTO> result = new ArrayList<>();

        for (Object[] row : raw) {
            String skillName = (String) row[0];
            String category  = (String) row[1];
            long count       = ((Number) row[2]).longValue();
            result.add(new SkillDemandDTO(skillName, category, count));
        }

        return result;
    }

    //  Resumo de reputação do profissional 

    private ReputationSummaryDTO buildReputationSummary(Long professionalId) {
        // Sempre calculado (nunca vazio) — com os priors neutros quando ainda não há
        // avaliação, em vez de zerar tudo e parecer reputação péssima.
        ReputationMetrics m = reputationService.getMetricsForProfessional(professionalId);

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
