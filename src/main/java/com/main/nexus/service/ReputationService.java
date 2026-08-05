package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CompanyRejectionReason;
import com.main.nexus.model.enums.NegativeReason;
import com.main.nexus.model.enums.PositiveReason;
import com.main.nexus.model.enums.ProfessionalRejectionReason;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.RejectionFeedbackRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import com.main.nexus.repository.ReviewRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReputationService {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private RejectionFeedbackRepository rejectionFeedbackRepository;
    @Autowired private ReputationMetricsRepository reputationMetricsRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private CompanyRepository companyRepository;

    // Constantes de configuração
    private static final double RECENT_WEIGHT = 0.70;
    private static final double HISTORICAL_WEIGHT = 0.30;
    private static final double MAX_ADJUSTMENT = 0.20;
    private static final double CONFIDENCE_THRESHOLD = 50.0;
    private static final double NEUTRAL_SCORE = 50.0;
    private static final int RECENT_MONTHS = 6;

    private static final double BAYESIAN_CONFIDENCE_CONSTANT = 5.0; // C — peso de "avaliações neutras" na média bayesiana
    private static final double BAYESIAN_GLOBAL_PRIOR = 50.0;       // m — média global assumida para um indicador 0-100

    // API PÚBLICA — consumida pelo MatchService

    public double getScoreAdjustment(Long entityId, AuthorType type) {
        ReputationMetrics metrics = (type == AuthorType.PROFESSIONAL)
                ? getOrCalculateForProfessional(entityId)
                : getOrCalculateForCompany(entityId);

        if (metrics.getReputationScore() == null || metrics.getConfidenceScore() == null) {
            return 0.0; // sem dados suficientes — neutro absoluto
        }

        double rawAdjustment = (metrics.getReputationScore() - NEUTRAL_SCORE) / NEUTRAL_SCORE * MAX_ADJUSTMENT;
        return rawAdjustment * metrics.getConfidenceScore();
    }

    // Nota consolidada (0-100) do ReputationMetrics, usada como componente aditivo do
    // score de match — sem a compressão em torno de NEUTRAL_SCORE aplicada em getScoreAdjustment.
    public double getReputationScore(Long entityId, AuthorType type) {
        ReputationMetrics metrics = (type == AuthorType.PROFESSIONAL)
                ? getOrCalculateForProfessional(entityId)
                : getOrCalculateForCompany(entityId);

        return metrics.getReputationScore() != null ? metrics.getReputationScore() : NEUTRAL_SCORE;
    }

    // RECÁLCULO — disparado por evento (Review ou RejectionFeedback novos)

    public void recalculateForProfessional(Long professionalId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Professional not found"));

        ReputationMetrics metrics = reputationMetricsRepository.findByProfessionalId(professionalId)
                .orElseGet(() -> {
                    ReputationMetrics m = new ReputationMetrics();
                    m.setProfessional(professional);
                    return m;
                });

        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(RECENT_MONTHS);

        // Só reviews escritas PELA empresa SOBRE esse profissional — sem esse filtro, uma
        // review que o próprio profissional escreveu sobre a empresa (mesmo match) entrava
        // na conta de reputação dele.
        List<Review> allReviews = reviewRepository.findByMatchProfessionalId(professionalId)
                .stream()
                .filter(r -> r.getAuthorType() == AuthorType.COMPANY)
                .toList();
        List<Review> recentReviews = allReviews.stream()
                .filter(r -> r.getCreatedAt().isAfter(sixMonthsAgo))
                .toList();

        List<RejectionFeedback> allRejections = rejectionFeedbackRepository.findByMatchProfessionalAndRejectedBy(
                professionalId, AuthorType.COMPANY, LocalDateTime.now().minusYears(50));
        List<RejectionFeedback> recentRejections = allRejections.stream()
                .filter(r -> r.getCreatedAt().isAfter(sixMonthsAgo))
                .toList();

        applyProfessionalCalculation(metrics, allReviews, recentReviews, allRejections, recentRejections);

        metrics.setLastCalculatedAt(LocalDateTime.now());
        reputationMetricsRepository.save(metrics);

        // Nota simples de estrelas (0-5) exibida em cards/perfil — independente do
        // ReputationMetrics analítico acima, que é usado só pro ajuste de score.
        updateStarRating(professional, allReviews);
    }

    public void recalculateForCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Company not found"));

        ReputationMetrics metrics = reputationMetricsRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    ReputationMetrics m = new ReputationMetrics();
                    m.setCompany(company);
                    return m;
                });

        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(RECENT_MONTHS);

        // Só reviews escritas PELO profissional SOBRE essa empresa — mesmo raciocínio do
        // lado do profissional: evita misturar a review que a empresa escreveu sobre o
        // profissional (mesmo match) na conta de reputação da empresa.
        List<Review> allReviews = reviewRepository.findByMatchProjectCompanyId(companyId)
                .stream()
                .filter(r -> r.getAuthorType() == AuthorType.PROFESSIONAL)
                .toList();
        List<Review> recentReviews = allReviews.stream()
                .filter(r -> r.getCreatedAt().isAfter(sixMonthsAgo))
                .toList();

        List<RejectionFeedback> allRejections = rejectionFeedbackRepository.findByMatchProjectCompanyAndRejectedBy(
                companyId, AuthorType.PROFESSIONAL, LocalDateTime.now().minusYears(50));
        List<RejectionFeedback> recentRejections = allRejections.stream()
                .filter(r -> r.getCreatedAt().isAfter(sixMonthsAgo))
                .toList();

        applyCompanyCalculation(metrics, allReviews, recentReviews, allRejections, recentRejections);

        metrics.setLastCalculatedAt(LocalDateTime.now());
        reputationMetricsRepository.save(metrics);

        // Nota simples de estrelas (0-5) exibida em cards/perfil — independente do
        // ReputationMetrics analítico acima, que é usado só pro ajuste de score.
        updateStarRating(company, allReviews);
    }

    private void updateStarRating(Professional professional, List<Review> reviews) {
        double average = reviews.isEmpty() ? 0.0
                : reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        professional.setReputation(average);
        professionalRepository.save(professional);
    }

    private void updateStarRating(Company company, List<Review> reviews) {
        double average = reviews.isEmpty() ? 0.0
                : reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        company.setReputation(average);
        companyRepository.save(company);
    }

    // Helpers de leitura

    private ReputationMetrics getOrCalculateForProfessional(Long professionalId) {
        return reputationMetricsRepository.findByProfessionalId(professionalId)
                .orElseGet(() -> {
                    recalculateForProfessional(professionalId);
                    return reputationMetricsRepository.findByProfessionalId(professionalId).orElseThrow();
                });
    }

    private ReputationMetrics getOrCalculateForCompany(Long companyId) {
        return reputationMetricsRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    recalculateForCompany(companyId);
                    return reputationMetricsRepository.findByCompanyId(companyId).orElseThrow();
                });
    }

    // CÁLCULO — PROFISSIONAL

    private void applyProfessionalCalculation(
            ReputationMetrics metrics,
            List<Review> allReviews, List<Review> recentReviews,
            List<RejectionFeedback> allRejections, List<RejectionFeedback> recentRejections) {

        // Technical Competence: positivos (HIGH_TECHNICAL_SKILL, HIGH_CODE_QUALITY, GOOD_PROBLEM_SOLVING)
        //                         + rejeições recebidas por MISSING_REQUIRED_SKILLS / INSUFFICIENT_EXPERIENCE
        double techHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.HIGH_TECHNICAL_SKILL, PositiveReason.HIGH_CODE_QUALITY, PositiveReason.GOOD_PROBLEM_SOLVING),
                List.of(NegativeReason.LOW_CODE_QUALITY, NegativeReason.POOR_PROBLEM_SOLVING));
        double techRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.HIGH_TECHNICAL_SKILL, PositiveReason.HIGH_CODE_QUALITY, PositiveReason.GOOD_PROBLEM_SOLVING),
                List.of(NegativeReason.LOW_CODE_QUALITY, NegativeReason.POOR_PROBLEM_SOLVING));
        double techRejectionPenaltyHist = rejectionRatioForProfessional(allRejections,
                List.of(CompanyRejectionReason.MISSING_REQUIRED_SKILLS, CompanyRejectionReason.INSUFFICIENT_EXPERIENCE));
        double techRejectionPenaltyRecent = rejectionRatioForProfessional(recentRejections,
                List.of(CompanyRejectionReason.MISSING_REQUIRED_SKILLS, CompanyRejectionReason.INSUFFICIENT_EXPERIENCE));

        metrics.setTechnicalCompetence(
                blend(techRecent - techRejectionPenaltyRecent, techHist - techRejectionPenaltyHist));

        // Communication
        double commHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.EXCELLENT_COMMUNICATION), List.of(NegativeReason.POOR_COMMUNICATION));
        double commRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.EXCELLENT_COMMUNICATION), List.of(NegativeReason.POOR_COMMUNICATION));
        metrics.setCommunication(blend(commRecent, commHist));

        // Reliability
        double relHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.RELIABLE, PositiveReason.DELIVERED_ON_TIME),
                List.of(NegativeReason.MISSED_DEADLINES, NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        double relRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.RELIABLE, PositiveReason.DELIVERED_ON_TIME),
                List.of(NegativeReason.MISSED_DEADLINES, NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        metrics.setReliability(blend(relRecent, relHist));
        
        // Punctuality 
        double punHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.PUNCTUAL, PositiveReason.DELIVERED_ON_TIME), List.of(NegativeReason.MISSED_DEADLINES));
        double punRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.PUNCTUAL, PositiveReason.DELIVERED_ON_TIME), List.of(NegativeReason.MISSED_DEADLINES));
        metrics.setPunctuality(blend(punRecent, punHist));

        // Professionalism
        double profHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.TEAM_PLAYER, PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.DID_NOT_MEET_EXPECTATIONS, NegativeReason.OTHER));
        double profRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.TEAM_PLAYER, PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.DID_NOT_MEET_EXPECTATIONS, NegativeReason.OTHER));
        metrics.setProfessionalism(blend(profRecent, profHist));

        // Satisfaction e Recommendation rate
        metrics.setSatisfactionAverage(bayesianRating(allReviews));
        metrics.setRecommendationRate(recommendationRate(allReviews));

        // Consolidação
        double reputationScore =
                (metrics.getTechnicalCompetence() * 0.25)
              + (metrics.getCommunication()        * 0.15)
              + (metrics.getReliability()           * 0.20)
              + (metrics.getPunctuality()            * 0.15)
              + (metrics.getProfessionalism()        * 0.15)
              + (metrics.getRecommendationRate()     * 0.10);

        metrics.setReputationScore(reputationScore);
        metrics.setTotalReviews(allReviews.size());
        metrics.setTotalReviewsRecent(recentReviews.size());
        metrics.setTotalRejectionsReceived(allRejections.size());
        metrics.setConfidenceScore(Math.min(1.0, (double) allReviews.size() / CONFIDENCE_THRESHOLD));
    }
    
    // CÁLCULO  EMPRESA igual ao profissional motivos diferentes

    private void applyCompanyCalculation(
            ReputationMetrics metrics,
            List<Review> allReviews, List<Review> recentReviews,
            List<RejectionFeedback> allRejections, List<RejectionFeedback> recentRejections) {

        double commHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.EXCELLENT_COMMUNICATION), List.of(NegativeReason.POOR_COMMUNICATION));
        double commRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.EXCELLENT_COMMUNICATION), List.of(NegativeReason.POOR_COMMUNICATION));
        metrics.setCommunication(blend(commRecent, commHist));

        double relHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.RELIABLE), List.of(NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        double relRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.RELIABLE), List.of(NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        double relRejectionPenaltyHist = rejectionRatioForCompany(allRejections,
                List.of(ProfessionalRejectionReason.HIRING_FROZEN, ProfessionalRejectionReason.PROJECT_CANCELLED));
        double relRejectionPenaltyRecent = rejectionRatioForCompany(recentRejections,
                List.of(ProfessionalRejectionReason.HIRING_FROZEN, ProfessionalRejectionReason.PROJECT_CANCELLED));
        metrics.setReliability(blend(relRecent - relRejectionPenaltyRecent, relHist - relRejectionPenaltyHist));

        double punHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.PUNCTUAL), List.of(NegativeReason.MISSED_DEADLINES));
        double punRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.PUNCTUAL), List.of(NegativeReason.MISSED_DEADLINES));
        metrics.setPunctuality(blend(punRecent, punHist));

        double profHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.OTHER));
        double profRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.OTHER));
        metrics.setProfessionalism(blend(profRecent, profHist));

        // Empresa não tem "competência técnica" própria, e usado o mesmo peso redistribuído
        // entre os 4 indicadores restantes (sem technicalCompetence)
        metrics.setTechnicalCompetence(null); // não se aplica a empresas

        metrics.setSatisfactionAverage(bayesianRating(allReviews));
        metrics.setRecommendationRate(recommendationRate(allReviews));

        double reputationScore =
                (metrics.getCommunication()    * 0.30)
              + (metrics.getReliability()       * 0.30)
              + (metrics.getPunctuality()        * 0.20)
              + (metrics.getProfessionalism()    * 0.10)
              + (metrics.getRecommendationRate() * 0.10);

        metrics.setReputationScore(reputationScore);
        metrics.setTotalReviews(allReviews.size());
        metrics.setTotalReviewsRecent(recentReviews.size());
        metrics.setTotalRejectionsReceived(allRejections.size());
        metrics.setConfidenceScore(Math.min(1.0, (double) allReviews.size() / CONFIDENCE_THRESHOLD));
    }

    // MATEMÁTICA AUXILIAR

    // Combina recente (70%) e histórico (30%)
    private double blend(double recent, double historical) {
        return (RECENT_WEIGHT * recent) + (HISTORICAL_WEIGHT * historical);
    }

    // Indicador 0-100 a partir da contagem de motivos positivos e negativos nas reviews, via Bayesian 
    private double indicatorFromReviews(List<Review> reviews, List<PositiveReason> positives, List<NegativeReason> negatives) {
        if (reviews.isEmpty()) return BAYESIAN_GLOBAL_PRIOR;

        long positiveCount = reviews.stream()
                .filter(r -> r.getPositiveReasons() != null)
                .flatMap(r -> r.getPositiveReasons().stream())
                .filter(positives::contains)
                .count();

        long negativeCount = reviews.stream()
                .filter(r -> r.getNegativeReasons() != null)
                .flatMap(r -> r.getNegativeReasons().stream())
                .filter(negatives::contains)
                .count();

        long totalMentions = positiveCount + negativeCount;
        if (totalMentions == 0) return BAYESIAN_GLOBAL_PRIOR;

        double rawRatio = ((double) positiveCount / totalMentions) * 100.0;

        // Bayesian: puxa para o prior global quando há poucas menções
        return ((BAYESIAN_CONFIDENCE_CONSTANT * BAYESIAN_GLOBAL_PRIOR) + (rawRatio * totalMentions))
                / (BAYESIAN_CONFIDENCE_CONSTANT + totalMentions);
    }

    // Nota média bayesiana, 0-5
    private double bayesianRating(List<Review> reviews) {
        if (reviews.isEmpty()) return 3.5; // prior neutro de 5 estrelas

        double sum = reviews.stream().mapToInt(Review::getRating).sum();
        int n = reviews.size();
        double priorMean = 3.5;
        double C = BAYESIAN_CONFIDENCE_CONSTANT;

        return ((C * priorMean) + sum) / (C + n);
    }

    private double recommendationRate(List<Review> reviews) {
        if (reviews.isEmpty()) return BAYESIAN_GLOBAL_PRIOR;
        long recommended = reviews.stream().filter(r -> r.getRating() >= 4).count();
        return ((double) recommended / reviews.size()) * 100.0;
    }

    // proporcao de rejeições recebidas pelo profissional com um motivo específico convertida em penalidade 0-30
    private double rejectionRatioForProfessional(List<RejectionFeedback> rejections, List<CompanyRejectionReason> targetReasons) {
        if (rejections.isEmpty()) return 0.0;

        long matching = rejections.stream()
                .filter(r -> r.getCompanyReasons() != null)
                .filter(r -> r.getCompanyReasons().stream().anyMatch(targetReasons::contains))
                .count();

        double ratio = (double) matching / rejections.size();
        return ratio * 30.0; // até 30 pontos de penalidade no indicador, proporcional
    }

    private double rejectionRatioForCompany(List<RejectionFeedback> rejections, List<ProfessionalRejectionReason> targetReasons) {
        if (rejections.isEmpty()) return 0.0;

        long matching = rejections.stream()
                .filter(r -> r.getProfessionalReasons() != null)
                .filter(r -> r.getProfessionalReasons().stream().anyMatch(targetReasons::contains))
                .count();

        double ratio = (double) matching / rejections.size();
        return ratio * 30.0;
    }
}