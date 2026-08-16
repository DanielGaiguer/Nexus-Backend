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

    @Autowired 
    private ReviewRepository reviewRepository;
    
    @Autowired 
    private RejectionFeedbackRepository rejectionFeedbackRepository;
    
    @Autowired 
    private ReputationMetricsRepository reputationMetricsRepository;
    
    @Autowired 
    private ProfessionalRepository professionalRepository;
    
    @Autowired 
    private CompanyRepository companyRepository;
    

    // Constantes de configuração
    private static final double RECENT_WEIGHT = 0.70;
    private static final double HISTORICAL_WEIGHT = 0.30;
    private static final double MAX_ADJUSTMENT = 0.20;
    private static final double CONFIDENCE_THRESHOLD = 10.0;
    private static final double NEUTRAL_SCORE = 50.0;
    private static final int RECENT_MONTHS = 6;

    private static final double BAYESIAN_CONFIDENCE_CONSTANT = 5.0; // C — peso de "avaliações neutras" na média bayesiana
    private static final double BAYESIAN_GLOBAL_PRIOR = 50.0;       // m — média global assumida para um indicador 0-100

    // Reescala de EXIBIÇÃO aplicada depois do cálculo bayesiano acima, que continua com prior
    // simétrico (50 no meio de 0-100, 3.5 perto do meio de 1-5) e portanto reage na MESMA
    // proporção pra evidência positiva e negativa. A reescala só remapeia o resultado final pra
    // uma faixa com "chão" mais alto uma variação de X% do valor bruto vira X% da faixa nova,
    // preservando a simetria da reação em vez de simplesmente mover o prior (que quebraria essa
    // proporção — ver rescaleIndicator/rescaleSatisfaction).
    private static final double INDICATOR_DISPLAY_FLOOR = 70.0;          // indicadores 0-100 -> faixa [70,100], média neutra vira 85
    private static final double SATISFACTION_DISPLAY_FLOOR = 11.0 / 3.0; // nota 1-5 -> faixa [3.667,5], média neutra vira 4.5
    private static final double SATISFACTION_CONFIDENCE_CONSTANT = 1.0;  // C próprio da nota de satisfação — bem mais reativo que o C=5 dos indicadores, pra cada avaliação pesar mais rápido no valor exibido

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

    // ReputationMetrics completo, sempre com os priors neutros já calculados (nunca
    // retorna vazio) — usado pelas telas de analytics pra não mostrar "reputação zerada"
    // pra quem simplesmente ainda não tem avaliação.
    public ReputationMetrics getMetricsForProfessional(Long professionalId) {
        return getOrCalculateForProfessional(professionalId);
    }

    public ReputationMetrics getMetricsForCompany(Long companyId) {
        return getOrCalculateForCompany(companyId);
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

        //Todas as rejeicoes 
        List<RejectionFeedback> allRejections = rejectionFeedbackRepository.findByMatchProfessionalAndRejectedBy(
                professionalId, AuthorType.COMPANY);
        // De 6 meses atras
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

    // Espelho do metodo para profissionais
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
                companyId, AuthorType.PROFESSIONAL);
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

    // - Professional.reputation/Company.reputation (média crua 0–5, exibida em cards de UI, e usada como componente aditivo ponderado dentro de getScore);
    // - ReputationMetrics.reputationScore (composto ponderado 0–100 com suavização bayesiana, usado apenas no multiplicador getScoreAdjustment).

    
    // Nulo enquanto nao houver nenhuma avaliacao — o front distingue "sem nota" de "nota 0"
    private void updateStarRating(Professional professional, List<Review> reviews) {
        Double average = reviews.isEmpty() ? null
                : reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        // Calcula a media das avaliacoess
        professional.setReputation(average);
        professionalRepository.save(professional);
    }

    private void updateStarRating(Company company, List<Review> reviews) {
        Double average = reviews.isEmpty() ? null
                : reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        company.setReputation(average);
        companyRepository.save(company);
    }
    
    //Um recém-cadastrado com uma única review de 5 estrelas teria Professional.reputation = 5.0 (nota "crua" máxima, sem suavização), 
    //mas seu ReputationMetrics.reputationScore ainda estaria fortemente puxado para o prior neutro (por causa da suavização bayesiana com poucos dados) 
    // e seu confidenceScore seria quase zero — nesse caso, o efeito líquido sobre o score de match seria: componente aditivo alto (pela nota crua), 
    // mas multiplicador quase neutro (pela baixa confiança).

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

        metrics.setTechnicalCompetence(rescaleIndicator(
                blend(techRecent - techRejectionPenaltyRecent, techHist - techRejectionPenaltyHist)));

        // Communication
        double commHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.EXCELLENT_COMMUNICATION), List.of(NegativeReason.POOR_COMMUNICATION));
        double commRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.EXCELLENT_COMMUNICATION), List.of(NegativeReason.POOR_COMMUNICATION));
        metrics.setCommunication(rescaleIndicator(blend(commRecent, commHist)));

        // Reliability
        double relHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.RELIABLE, PositiveReason.DELIVERED_ON_TIME),
                List.of(NegativeReason.MISSED_DEADLINES, NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        double relRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.RELIABLE, PositiveReason.DELIVERED_ON_TIME),
                List.of(NegativeReason.MISSED_DEADLINES, NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        metrics.setReliability(rescaleIndicator(blend(relRecent, relHist)));

        // Punctuality
        double punHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.PUNCTUAL, PositiveReason.DELIVERED_ON_TIME), List.of(NegativeReason.MISSED_DEADLINES));
        double punRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.PUNCTUAL, PositiveReason.DELIVERED_ON_TIME), List.of(NegativeReason.MISSED_DEADLINES));
        metrics.setPunctuality(rescaleIndicator(blend(punRecent, punHist)));

        // Professionalism
        double profHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.TEAM_PLAYER, PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.DID_NOT_MEET_EXPECTATIONS, NegativeReason.OTHER));
        double profRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.TEAM_PLAYER, PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.DID_NOT_MEET_EXPECTATIONS, NegativeReason.OTHER));
        metrics.setProfessionalism(rescaleIndicator(blend(profRecent, profHist)));

        // Satisfaction e Recommendation rate recommendationRate fica de fora da reescala de
        // propósito (é % direto de reviews com nota >= 4, sem suavização bayesiana; reescalar
        // junto misturaria uma métrica "crua" com métricas já compostas).
        metrics.setSatisfactionAverage(rescaleSatisfaction(bayesianRating(allReviews)));
        metrics.setRecommendationRate(recommendationRate(allReviews));

        // Consolidação
        double reputationScore =
                (metrics.getTechnicalCompetence() * 0.25)
              + (metrics.getCommunication()        * 0.15)
              + (metrics.getReliability()           * 0.20)
              + (metrics.getPunctuality()            * 0.15)
              + (metrics.getProfessionalism()        * 0.15)
              + (metrics.getRecommendationRate()     * 0.10);
        
        // Os pesos exatos, somando 1.00: técnica 25%, confiabilidade 20%, comunicação 15%, pontualidade 15%, profissionalismo 15%, taxa de recomendação 10%. 
        // Cada indicador é primeiro calculado via indicatorFromReviews (com o par de motivos positivos/negativos específico — ex.: technicalCompetence 
        // usa HIGH_TECHNICAL_SKILL/HIGH_CODE_QUALITY/GOOD_PROBLEM_SOLVING como positivos e LOW_CODE_QUALITY/POOR_PROBLEM_SOLVING como negativos), 
        // depois blendado 70/30 entre recente e histórico, e technicalCompetence e reliability ainda sofrem a subtração da penalidade de rejeição correspondente antes do blend.

        metrics.setReputationScore(reputationScore);
        metrics.setTotalReviews(allReviews.size());
        metrics.setTotalReviewsRecent(recentReviews.size());
        metrics.setTotalRejectionsReceived(allRejections.size());
        metrics.setConfidenceScore(Math.min(1.0, (double) allReviews.size() / CONFIDENCE_THRESHOLD));
        
        // CONFIDENCE_THRESHOLD = 10.0 — ou seja, a confiança atinge o máximo (1.0) a partir de 10 reviews recebidas (allReviews.size(), o total histórico, não o recente),
        // crescendo linearmente até lá. Com 0 reviews, confiança = 0; com 5 reviews, confiança = 0.5; com 10+ reviews, confiança = 1.0 (o Math.min trava o teto). 
        // É esse valor que, como vimos em getScoreAdjustment, multiplica o rawAdjustment — é a peça final que impede que um profissional/empresa com 
        // uma única review de 1 estrela receba imediatamente o ajuste completo de -20% no score de match.
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
        metrics.setCommunication(rescaleIndicator(blend(commRecent, commHist)));

        double relHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.RELIABLE), List.of(NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        double relRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.RELIABLE), List.of(NegativeReason.UNRELIABLE, NegativeReason.ABSENT));
        double relRejectionPenaltyHist = rejectionRatioForCompany(allRejections,
                List.of(ProfessionalRejectionReason.HIRING_FROZEN, ProfessionalRejectionReason.PROJECT_CANCELLED));
        double relRejectionPenaltyRecent = rejectionRatioForCompany(recentRejections,
                List.of(ProfessionalRejectionReason.HIRING_FROZEN, ProfessionalRejectionReason.PROJECT_CANCELLED));
        metrics.setReliability(rescaleIndicator(blend(relRecent - relRejectionPenaltyRecent, relHist - relRejectionPenaltyHist)));

        double punHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.PUNCTUAL), List.of(NegativeReason.MISSED_DEADLINES));
        double punRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.PUNCTUAL), List.of(NegativeReason.MISSED_DEADLINES));
        metrics.setPunctuality(rescaleIndicator(blend(punRecent, punHist)));

        double profHist = indicatorFromReviews(allReviews,
                List.of(PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.OTHER));
        double profRecent = indicatorFromReviews(recentReviews,
                List.of(PositiveReason.PROACTIVE, PositiveReason.EXCEEDED_EXPECTATIONS),
                List.of(NegativeReason.UNPROFESSIONAL, NegativeReason.OTHER));
        metrics.setProfessionalism(rescaleIndicator(blend(profRecent, profHist)));

        // Empresa não tem "competência técnica" própria, e usado o mesmo peso redistribuído
        // entre os 4 indicadores restantes (sem technicalCompetence)
        metrics.setTechnicalCompetence(null); // não se aplica a empresas

        metrics.setSatisfactionAverage(rescaleSatisfaction(bayesianRating(allReviews)));
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

    // Remapeia um indicador bruto 0-100 (já bayesiano/blendado) pra [INDICATOR_DISPLAY_FLOOR,100].
    // reputationScore herda essa compressão automaticamente, porque é somado a partir dos
    // getters dos indicadores (já reescalados), não dos valores brutos de blend(...).
    
    
    // Ou seja, o valor original de 0–100 é comprimido para uma escala de 60–100.
    // e usado quando quer que um indicador nunca seja visualmente menor que determinado valor, mesmo quando o valor real é baixo
    private double rescaleIndicator(double raw) {
        return INDICATOR_DISPLAY_FLOOR + (raw / 100.0) * (100.0 - INDICATOR_DISPLAY_FLOOR);
    }

    // Mesma ideia pra nota de satisfação, só que na escala 1-5 em vez de 0-100.
    private double rescaleSatisfaction(double raw) {
        return SATISFACTION_DISPLAY_FLOOR + ((raw - 1.0) / 4.0) * (5.0 - SATISFACTION_DISPLAY_FLOOR);
    }

    // Indicador 0-100 a partir da contagem de motivos positivos e negativos nas reviews, via Bayesian 
    // A ideia central é:
    //Se existem poucas menções, não confiar cegamente nelas.
    // Se existem muitas menções, confiar cada vez mais no resultado real.
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
        if (totalMentions == 0) return BAYESIAN_GLOBAL_PRIOR; // positiveCount = 0 negativeCount = 0 ; não existe informação suficiente para calcular o indicador.

        double rawRatio = ((double) positiveCount / totalMentions) * 100.0; // calcula a proporção de menções positivas.

        // Média Bayesiana: combina o resultado real das avaliações com um valor global de referência (prior),
        // evitando que poucas avaliações gerem resultados extremos ou pouco confiáveis. O BAYESIAN_GLOBAL_PRIOR representa o valor inicial de referência,
        // neste caso 50, que funciona como uma avaliação neutra. O BAYESIAN_CONFIDENCE_CONSTANT representa o peso dado a esse valor inicial; como seu valor é 5,
        // é como se o sistema considerasse que o prior possui o peso equivalente a 5 menções. O rawRatio representa a porcentagem real de 
        // menções positivas entre todas as menções positivas  e negativas, enquanto totalMentions representa a quantidade de evidências reais disponíveis. 
        // Dessa forma, quando existem poucas menções, o resultado é mais influenciado pelo prior global, evitando notas muito altas ou baixas com pouca informação. Conforme o número de 
        // menções aumenta, as avaliações reais passam a ter mais peso e o resultado se aproxima cada vez mais da proporção observada. 
        //Assim, a média Bayesiana permite obter um indicador mais confiável e justo mesmo quando algumas empresas possuem poucas avaliações.
        
        // puxa para o prior global quando há poucas menções
        //              5.0                           50
        return ((BAYESIAN_CONFIDENCE_CONSTANT * BAYESIAN_GLOBAL_PRIOR) + (rawRatio * totalMentions))
                / (BAYESIAN_CONFIDENCE_CONSTANT + totalMentions);
        // BAYESIAN_GLOBAL_PRIOR = O prior funciona como uma "âncora" para evitar resultados extremos quando existe pouca informação.
        // BAYESIAN_CONFIDENCE_CONSTANT = Antes de analisar as avaliações dessa empresa, vou considerar que tenho uma confiança equivalente a 5 observações no valor global de 50.
        // O sistema diz: "Eu já parto de uma opinião neutra de 50, com uma confiança equivalente a 5 avaliações."
        
        // A formula pode ser entendida como: 
//                     PRIOR + DADOS REAIS
//        Resultado = -----------------------
//                        PESOS TOTAIS

    // O efeito prático: com poucas menções (n pequeno), o resultado fica puxado para perto de 50 (o prior domina); 
    // com muitas menções (n grande, n >> C), o resultado converge para o rawRatio real observado (os dados dominam sobre o prior). 
    // Isso evita que um único review muito positivo ou muito negativo distorça um indicador para 0 ou 100 de forma extrema — 
    // é uma proteção estatística contra ruído de amostra pequena, com o mesmo espírito por trás do confidenceScore visto em getScoreAdjustment.
    }

    // Nota média bayesiana, 0-5
    private double bayesianRating(List<Review> reviews) {
        if (reviews.isEmpty()) return 3.5; // é o prior, ou seja, a nota inicial que o sistema assume quando não possui informações suficientes.

        double sum = reviews.stream().mapToInt(Review::getRating).sum(); // Conta a nota de cada avaliacao
        int n = reviews.size(); // Quantidade de avaliacoes
        double priorMean = 3.5; //considero uma nota neutra de 3,5 estrelas
        double C = SATISFACTION_CONFIDENCE_CONSTANT; // C próprio da satisfação — mais baixo que o dos indicadores, reage mais rápido por avaliação

        return ((C * priorMean) + sum) / (C + n);
    }

    private double recommendationRate(List<Review> reviews) {
        if (reviews.isEmpty()) return BAYESIAN_GLOBAL_PRIOR;
        long recommended = reviews.stream().filter(r -> r.getRating() >= 4).count();
        return ((double) recommended / reviews.size()) * 100.0;
    }

    // A lógica: dentre todas as rejeições que o profissional recebeu (de um período — recente ou histórico, dependendo de qual lista é passada), 
    // quantas tiveram um motivo especificamente relacionado ao indicador que está sendo calculado (ex.: para technicalCompetence, 
    // os motivos-alvo são MISSING_REQUIRED_SKILLS/INSUFFICIENT_EXPERIENCE). Essa proporção (ratio, de 0.0 a 1.0) é multiplicada por 30.0, gerando uma penalidade de 0 a 30 pontos 
    // Essa penalidade é então subtraída do valor do indicador correspondente (indicatorFromReviews(...) - rejectionRatioForXxx(...)), tanto na versão "recente" quanto "histórica",
    // antes de aplicar o blend 70/30
    
    // proporcao de rejeições recebidas pelo profissional com um motivo específico convertida em penalidade 0-30
    private double rejectionRatioForProfessional(List<RejectionFeedback> rejections, List<CompanyRejectionReason> targetReasons) {
        if (rejections.isEmpty()) return 0.0;

        long matching = rejections.stream()
                .filter(r -> r.getCompanyReasons() != null)
                .filter(r -> r.getCompanyReasons().stream().anyMatch(targetReasons::contains)) // Existe pelo menos um motivo dessa rejeição que está dentro dos motivos que estou procurando?
                .count();

        double ratio = (double) matching / rejections.size(); // Calcula a proporcao
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