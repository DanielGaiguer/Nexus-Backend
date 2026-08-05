package com.main.nexus.service;

import com.main.nexus.dto.ScoreBreakdownDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchHistory;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CompanyRejectionReason;
import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.InitiatedBy;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProfessionalRejectionReason;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.ProjectType;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchHistoryRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.RejectionFeedbackRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private RejectionFeedbackRepository rejectionFeedbackRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ReputationService reputationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private MatchHistoryService matchHistoryService;

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;
    
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProfileCompletionService profileCompletionService;

    // SCORE ENGINE — fórmula principal
    // ScoreMatch = (Skills*0.35) + (Budget*0.25) + (History*0.20) + (Reputation*0.10) + (Availability*0.10)
    // ou, para ONSITE/HYBRID:
    // ScoreMatch = (Skills*0.30) + (Budget*0.20) + (History*0.17) + (Reputation*0.09) + (Availability*0.09) + (Distance*0.15)
    public double getScore(Professional professional, Project project) {

        double skillScore        = calculateSkillScore(professional, project);
        double budgetScore       = calculateBudgetScore(professional, project);
        double historyScore      = calculateHistoryScore(professional);
        double reputationScore   = calculateReputationScore(professional);
        double availabilityScore = calculateAvailabilityScore(professional);

        boolean considersDistance   = project.getWorkMode() == Modality.ONSITE
                                    || project.getWorkMode() == Modality.HYBRID;
        boolean considersExperience = project.getExperienceLevel() != null;

        double baseScore;

        if (considersDistance && considersExperience) {
            // ONSITE/HYBRID com experiência (7 componentes)
            double distanceScore   = calculateDistanceScore(professional, project);
            double experienceScore = calculateExperienceScore(professional, project);

            baseScore = (skillScore        * 0.26)
                      + (budgetScore       * 0.18)
                      + (historyScore      * 0.15)
                      + (reputationScore   * 0.08)
                      + (availabilityScore * 0.08)
                      + (distanceScore     * 0.13)
                      + (experienceScore   * 0.12);

        } else if (considersDistance) {
            // ONSITE/HYBRID sem experiência (6 componentes)
            double distanceScore = calculateDistanceScore(professional, project);

            baseScore = (skillScore        * 0.30)
                      + (budgetScore       * 0.20)
                      + (historyScore      * 0.17)
                      + (reputationScore   * 0.09)
                      + (availabilityScore * 0.09)
                      + (distanceScore     * 0.15);

        } else if (considersExperience) {
            // REMOTO com experiência (6 componentes)
            double experienceScore = calculateExperienceScore(professional, project);

            baseScore = (skillScore        * 0.30)
                      + (budgetScore       * 0.22)
                      + (historyScore      * 0.18)
                      + (reputationScore   * 0.09)
                      + (availabilityScore * 0.09)
                      + (experienceScore   * 0.12);

        } else {
            // REMOTO sem experiência (5 componentes, fórmula original)
            baseScore = (skillScore        * 0.35)
                      + (budgetScore       * 0.25)
                      + (historyScore      * 0.20)
                      + (reputationScore   * 0.10)
                      + (availabilityScore * 0.10);
        }

        //Calculo de reputacao, do projeto e do profissional. O score ele nao e recalculado, e sim multiplicado para baixo ou para cima, dependendo se a reputacao for positiva ou negativa
        // um multiplicador global, limitado a ±20% (MAX_ADJUSTMENT = 0.20), centrado em 50 (NEUTRAL_SCORE) e ainda escalado pelo confidenceScore 
        // ou seja, se o profissional tem pouco histórico (poucas reviews/recusas), o ajuste fica perto de 0 mesmo que o score bruto seja ótimo ou péssimo
        double professionalAdjustment = reputationService.getScoreAdjustment(professional.getId(), AuthorType.PROFESSIONAL);
        double companyAdjustment = reputationService.getScoreAdjustment(project.getCompany().getId(), AuthorType.COMPANY);

        // Score final 
        return baseScore * (1 + professionalAdjustment + companyAdjustment);
    }

    //  Reconstrói o score componente a componente para exibição no front
    // ranking e comparação de candidatos usam o mesmo calculo, o finalScore
    // reaproveita match.getMatchScore(), o valor armazenado, o mesmo exibido em
    // toda outra tela do sistema para nunca divergir do que a empresa já viu
    // em outro lugar. Os componentes individuais são recalculados ao vivo,
    // já que nunca são persistidos.

    public ScoreBreakdownDTO getScoreBreakdown(Professional professional, Project project, Match match) {

        double skillScore        = calculateSkillScore(professional, project);
        double budgetScore       = calculateBudgetScore(professional, project);
        double historyScore      = calculateHistoryScore(professional);
        double reputationScore   = calculateReputationScore(professional);
        double availabilityScore = calculateAvailabilityScore(professional);

        boolean considersDistance   = project.getWorkMode() == Modality.ONSITE
                                    || project.getWorkMode() == Modality.HYBRID;
        boolean considersExperience = project.getExperienceLevel() != null;

        Double distanceScore   = considersDistance
                ? calculateDistanceScore(professional, project) : null;
        Double experienceScore = considersExperience
                ? calculateExperienceScore(professional, project) : null;

        double baseScore = computeBaseScoreForBreakdown( //O sistema recalcula os componentes individuais agora. Sao os dados atuais
                skillScore, budgetScore, historyScore, reputationScore,
                availabilityScore, distanceScore, experienceScore,
                considersDistance, considersExperience);

        double reputationAdjustment = match.getMatchScore() - baseScore; //A diferença entre o score final antigo e o score base recalculado agora.

        return new ScoreBreakdownDTO(
                roundToOneDecimal(skillScore),
                roundToOneDecimal(budgetScore),
                roundToOneDecimal(historyScore),
                roundToOneDecimal(reputationScore),
                roundToOneDecimal(availabilityScore),
                distanceScore   != null ? roundToOneDecimal(distanceScore)   : null,
                experienceScore != null ? roundToOneDecimal(experienceScore) : null,
                roundToOneDecimal(reputationAdjustment),
                roundToOneDecimal(match.getMatchScore()) // Score final oficial que foi persistido
        );
    }

    // Mesma fórmula de ponderação usada em getScore(), mas sem o multiplicador
    // de reputação, e juntado o base score para poder mostrar o quanto
    // desse multiplicador contribuiu (reputationAdjustment) separadamente.
    private double computeBaseScoreForBreakdown(
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

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // Skills: quantas skills da vaga o profissional possui / total exigido * 100
    public double calculateSkillScore(Professional professional, Project project) {
        List<Skill> required = project.getRequiredSkills();
        if (required == null || required.isEmpty()) return 100.0;

        List<String> professionalSkillNames = professional.getSkills()
                .stream()
                .map(s -> s.getName().toLowerCase())
                .toList();

        long matched = required.stream()
                .filter(s -> professionalSkillNames.contains(s.getName().toLowerCase()))
                .count();

        return ((double) matched / required.size()) * 100.0;
    }

    // Orçamento: para vagas CLT/PJ, compara a pretensão única do profissional contra o
    // range salarial da vaga via tabela de penalidade por % de diferença. Para vagas
    // freelance/temporárias e para PROJECT, e usada a lógica de faixa (min/max)
    public double calculateBudgetScore(Professional professional, Project project) {
        if (project.getOpportunityType() == OpportunityType.JOB && project.getContractType() != null) {
            return switch (project.getContractType()) {
                case CLT, INTERNSHIP -> calculateSalaryScore(
                        professional.getExpectedSalaryCLT(), project.getMonthlySalaryMax());
                case PJ -> calculateSalaryScore(
                        professional.getExpectedSalaryPJ(), project.getMonthlySalaryMax());
                case TEMPORARY, FREELANCER -> calculateRangeScore(
                        professional.getFreelanceMinExpectation(), project.getMonthlySalaryMax());
            };
        }

        // PROJECT usa a pretensão freelance (piso do profissional vs. teto do projeto)
        return calculateRangeScore(
                professional.getFreelanceMinExpectation(), project.getMaximumBudget());
    }

    // Compara um valor único de pretensão salarial (CLT/PJ) contra o teto ofertado pela vaga.
    // Só penaliza quando a pretensão ultrapassa o teto — a empresa genuinamente não pode/não
    // quer pagar aquilo. Pedir dentro ou abaixo do teto não é penalizado
    private double calculateSalaryScore(Double expected, Double offeredMax) {
        if (expected == null || offeredMax == null) return 50.0;

        if (expected <= offeredMax) {
            return 100.0;
        }

        double diffPercent = ((expected - offeredMax) / offeredMax) * 100.0; // por exemplo, offeredMax = 10.000, expected = 12.000
        // 12.000 - 10.000 = 2.000 ; 2.000 / 10.000 = 0.2 ; 0.2 * 100 = 20 ; diffPercent = 20.0
        return 100.0 - salaryPenaltyPercent(diffPercent);
    }

    // Tabela de abatimento no score conforme a diferença percentual entre esperado e ofertado
    private double salaryPenaltyPercent(double diffPercent) {
        if (diffPercent <= 15.0)  return 0.0;
        if (diffPercent <= 20.0)  return 10.0;
        if (diffPercent <= 30.0)  return 15.0;
        if (diffPercent <= 40.0)  return 20.0;
        if (diffPercent <= 50.0)  return 25.0;
        if (diffPercent <= 60.0)  return 30.0;
        if (diffPercent <= 75.0)  return 35.0;
        if (diffPercent <= 100.0) return 40.0;
        return 50.0;
    }

    //  Compara o piso da pretensão do profissional (profMin) contra o teto ofertado pelo projeto
    // (projMax): existe negociação viável sempre que profMin <= projMax, já que o
    // profissional aceita qualquer preço a partir do seu mínimo e a empresa paga
    // qualquer preço até o seu teto — o profMax do profissional e o projMin do projeto
    // não afetam essa viabilidade, por isso não entram na conta. Só penaliza quando o
    // profissional pede acima do teto do projeto; pedir dentro ou abaixo não é
    // penalizado (mais barato nunca é problema de fit para a empresa, e desalinhamento
    // de perfil já é capturado por calculateExperienceScore/calculateSkillScore). Fora
    // da faixa, aplica a mesma tabela de penalidade por diferença percentual usada em
    // calculateSalaryScore, unificando o critério entre os dois modos de comparação
    // salarial do sistema.
    private double calculateRangeScore(Double profMin, Double projMax) {
        if (profMin == null || projMax == null) return 50.0;

        if (profMin <= projMax) {
            return 100.0;
        }
        
        double diffPercent = ((profMin - projMax) / projMax) * 100.0; // Por exemplo, profMin = 12k, projMax = 10k
        //     12.000 - 10.000 = 2.00 ; 2.000 / 10.000 = 0.2 ; 0.2 * 100 = diffPercent = 20.0   
        return 100.0 - salaryPenaltyPercent(diffPercent);
    }

    // Pretensão salarial única e relevante para o regime da vaga/projeto e usada fora do
    // cálculo de score (filtros de ranking, comparação de candidatos)
    public Double calculateExpectedSalary(Professional professional, Project project) {
        if (project.getOpportunityType() == OpportunityType.JOB && project.getContractType() != null) {
            return switch (project.getContractType()) {
                case CLT, INTERNSHIP -> professional.getExpectedSalaryCLT();
                case PJ -> professional.getExpectedSalaryPJ();
                case TEMPORARY, FREELANCER -> averageOf(
                        professional.getFreelanceMinExpectation(), professional.getFreelanceMaxExpectation());
            };
        }
        return averageOf(professional.getFreelanceMinExpectation(), professional.getFreelanceMaxExpectation());
    }

    private Double averageOf(Double min, Double max) {
        if (min == null) return max;
        if (max == null) return min;
        return (min + max) / 2.0;
    }

    // historico e baseado na quantidade de projetos anteriores máx 5 projetos = 100
    public double calculateHistoryScore(Professional professional) {
        int count = professional.getProjects() != null
                ? professional.getProjects().size()
                : 0;
        return Math.min(count * 20.0, 100.0);
    }

    // reputação: nota consolidada (0-100) do ReputationMetrics — composto ponderado de
    // competência técnica, comunicação, confiabilidade, pontualidade, profissionalismo e
    // taxa de recomendação, calculado a partir das reviews reais recebidas pelo profissional
    // (ver ReputationService). Não é mais lida do campo simples Professional.reputation.
    public double calculateReputationScore(Professional professional) {
        return reputationService.getReputationScore(professional.getId(), AuthorType.PROFESSIONAL);
    }

    // Disponibilidade: disponível = 100, indisponível = 0
    public double calculateAvailabilityScore(Professional professional) {
        return Boolean.TRUE.equals(professional.getAvailable()) ? 100.0 : 0.0;
    }

    // distancia: calculada via Haversine entre profissional e empresa
    public double calculateDistanceScore(Professional professional, Project project) {
        Double profLat = professional.getLatitude();
        Double profLon = professional.getLongitude();

        // Usa a localização efetiva da vaga:
        // se o projeto tem CEP próprio, usa as coordenadas dele
        // senão, cai no CEP da empresa como fallback
        Double projectLat = project.getEffectiveLatitude();
        Double projectLon = project.getEffectiveLongitude();

        if (profLat == null || profLon == null || projectLat == null || projectLon == null) {
            return 50.0;
        }

        double distanceKm = haversineDistance(profLat, profLon, projectLat, projectLon);
        return scoreFromDistance(distanceKm);
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) { //Calculo de distancia pela latitude
        final double EARTH_RADIUS_KM = 6371.0;

        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                 + Math.cos(latRad1) * Math.cos(latRad2)
                 * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
    
    //até 5km = 100, até 10km = 90, até 20km = 75, até 50km = 50, até 100km = 25, acima disso = 10
    private double scoreFromDistance(double distanceKm) {
        if (distanceKm <= 10)   return 100.0;
        if (distanceKm <= 20)  return 90.0;
        if (distanceKm <= 30)  return 75.0;
        if (distanceKm <= 50)  return 50.0;
        if (distanceKm <= 100) return 25.0;
        return 10.0;
    }
    
    public double calculateExperienceScore(Professional professional, Project project) {
        ExperienceLevel profLevel = professional.getExperienceLevel();
        ExperienceLevel projLevel = project.getExperienceLevel();

        if (profLevel == null || projLevel == null) {
            return 50.0;
        }
        //posição no enum: INTERNSHIP=0, TRAINEE=1, JUNIOR=2, PLENO=3, SENIOR=4
        int distance = profLevel.ordinal() - projLevel.ordinal();

        if (distance == 0) return 100.0; //Se for a experiencia exata, 100%

        if (distance > 0) {
            // Profissional acima do nível pedido,  penalidade leve
            if (distance == 1) return 85.0;
            if (distance == 2) return 65.0;
            return 45.0; // distance >= 3
        } else {
            // Profissional abaixo do nível pedido, penalidade mais forte
            int absDistance = Math.abs(distance); //Tranforma o numero em positivo, caso seja negativo
            if (absDistance == 1) return 70.0;
            if (absDistance == 2) return 40.0;
            return 15.0; // absDistance >= 3
        }
    }
    
    // RANKING geração e calculo

    public List<Match> generateRankingForProject(Project project) {
        if (project.getStatus() != ProjectStatus.OPEN) {
            return List.of();
        }

        List<Professional> allProfessionals = professionalRepository.findAll();
        List<Match> matches = new ArrayList<>();

        for (Professional professional : allProfessionals) {
            // Verifica se o profissional Tem o minimo indispensavel para o calculo do score
            if (!profileCompletionService.canParticipateInRanking(professional)) continue;
            
            if (!matchesProjectType(professional, project)) continue;

            boolean alreadyExists = matchRepository
                    .findByProjectIdAndProfessionalId(project.getId(), professional.getId())
                    .isPresent();
            if (alreadyExists) continue;

            double score = getScore(professional, project);

            Match match = new Match();
            match.setProject(project);
            match.setProfessional(professional);
            match.setMatchScore(score);
            matches.add(match);

            if (score >= 90.0) {
                emailService.send(
                    professional.getUser().getEmail(),
                    "Nova oportunidade muito compatível com você!",
                    "Olá " + professional.getName() + ",\n\n" +
                    "Encontramos um projeto com " + String.format("%.0f", score) + "% de compatibilidade com seu perfil:\n\n" +
                    "\"" + project.getTitle() + "\" — " + project.getCompany().getCompanyName() + "\n\n" +
                    "Acesse o Nexus para ver os detalhes e demonstrar interesse.\n\nEquipe Nexus"
                );
                notificationService.notifyHighScoreOpportunity(
                    professional.getUser(),
                    project.getTitle(),
                    project.getCompany().getCompanyName(),
                    score,
                    project.getId()
                );

                emailService.send(
                    project.getCompany().getUser().getEmail(),
                    "Novo candidato muito compatível com seu projeto!",
                    "Olá " + project.getCompany().getCompanyName() + ",\n\n" +
                    professional.getName() + " tem " + String.format("%.0f", score) + "% de compatibilidade com o seu projeto:\n\n" +
                    "\"" + project.getTitle() + "\"\n\n" +
                    "Acesse o Nexus para ver o ranking e demonstrar interesse.\n\nEquipe Nexus"
                );
                notificationService.notifyHighScoreCandidate(
                    project.getCompany().getUser(),
                    professional.getName(),
                    project.getTitle(),
                    score,
                    project.getId()
                );
            }
        }

        matches.sort(Comparator.comparingDouble(Match::getMatchScore).reversed());
        return matchRepository.saveAll(matches);
    }

    @Transactional
    public void recalculateRankingForProject(Project project) {
        List<Match> existingMatches = matchRepository.findByProjectId(project.getId());

        for (Match match : existingMatches) {
            if (match.getStatus() == StatusMatch.WAITING) {
                double newScore = getScore(match.getProfessional(), project);
                match.setMatchScore(newScore);
            }
        }
        matchRepository.saveAll(existingMatches);

        generateRankingForProject(project);
    }

    private boolean matchesProjectType(Professional professional, Project project) {
        // Se o profissional nao tiver preferencia de projeto
        if (professional.getPreferredTypes() == null || professional.getPreferredTypes().isEmpty()) { 
            return true;
        }

        if (project.getOpportunityType() == OpportunityType.PROJECT) {
            // project.getType() é o mesmo enum ProjectType usado nas preferências do
            // profissional, então dá pra comparar direto — sem tabela de mapeamento.
            return project.getType() == null
                || professional.getPreferredTypes().contains(project.getType());
        }

        if (project.getOpportunityType() == OpportunityType.JOB) {
            if (project.getContractType() == null) return true;
            return switch (project.getContractType()) {
                case CLT -> professional.getPreferredTypes().contains(ProjectType.FULL_TIME) 
                        || professional.getPreferredTypes().contains(ProjectType.PART_TIME);
                    
                // Poderia ser PJ -> true, mas nao fiz isso para facilitar a leitura e a distincao visual
                case PJ -> professional.getPreferredTypes().contains(ProjectType.FULL_TIME)
                        || professional.getPreferredTypes().contains(ProjectType.PART_TIME) 
                        || professional.getPreferredTypes().contains(ProjectType.FREELANCE);
                    
                case INTERNSHIP -> professional.getPreferredTypes().contains(ProjectType.FULL_TIME) 
                    || professional.getPreferredTypes().contains(ProjectType.PART_TIME);
                    
                case TEMPORARY  -> professional.getPreferredTypes().contains(ProjectType.PART_TIME)
                                || professional.getPreferredTypes().contains(ProjectType.FREELANCE);
                    
                case FREELANCER -> professional.getPreferredTypes().contains(ProjectType.FREELANCE) 
                    || professional.getPreferredTypes().contains(ProjectType.PART_TIME);
            };
        }

        return true;
    }

    // FLUXO BILATERAL DE INTERESSE

    @Transactional
    public Match companyShowsInterest(Long matchId, Long companyId) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        if (match.getStatus() == StatusMatch.REJECTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match was rejected and cannot be reactivated.");
        }

        boolean wasAlreadyProfessionalInterested = match.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED;
        String fromStatus = match.getStatus().name();

        if (wasAlreadyProfessionalInterested) {
            assertProjectHasOpenPositions(match.getProject());
            match.setCompanyStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.MATCHED);
            incrementFilledPositions(match.getProject());
        } else {
            assertProjectIsOpen(match.getProject());
            match.setCompanyStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.COMPANY_INTERESTED);
            match.setInitiatedBy(InitiatedBy.COMPANY);
        }

        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "COMPANY");
        Match saved = matchRepository.save(match);

        if (wasAlreadyProfessionalInterested) {
            notifyMutualMatch(saved);
        } else {
            notificationService.notifyNewInvite(
                match.getProfessional().getUser(),
                match.getProject().getCompany().getCompanyName(),
                match.getProject().getTitle(),
                saved.getId()
            );
            Professional professional = match.getProfessional();
            emailService.send(
                professional.getUser().getEmail(),
                "Você recebeu um convite — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                match.getProject().getCompany().getCompanyName() + " demonstrou interesse no seu perfil " +
                "para o projeto \"" + match.getProject().getTitle() + "\".\n\n" +
                "Acesse o Nexus para aceitar ou recusar o convite.\n\nEquipe Nexus"
            );
        }

        return saved;
    }

    @Transactional
    public Match companyAccepts(Long matchId, Long companyId) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        if (match.getStatus() != StatusMatch.PROFESSIONAL_INTERESTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not awaiting a company response.");
        }
        assertProjectHasOpenPositions(match.getProject());

        String fromStatus = match.getStatus().name();
        match.setCompanyStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.MATCHED);
        incrementFilledPositions(match.getProject());
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "COMPANY");
        Match saved = matchRepository.save(match);
        notifyMutualMatch(saved);
        return saved;
    }

    public Match companyRejectsWithFeedback(Long matchId, Long companyId, List<CompanyRejectionReason> reasons) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        String fromStatus = match.getStatus().name();
        match.setCompanyStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "COMPANY");
        Match saved = matchRepository.save(match);

        saveCompanyRejection(match, reasons);
        notificationService.notifyInviteRejected(
            match.getProfessional().getUser(),
            match.getProject().getCompany().getCompanyName(),
            match.getProject().getTitle()
        );
        return saved;
    }

    @Transactional
    public Match companyCancelsMatch(Long matchId, Long companyId) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        boolean wasMatched = match.getStatus() == StatusMatch.MATCHED;
        boolean wasPendingInvite = match.getStatus() == StatusMatch.COMPANY_INTERESTED;

        if (!wasMatched && !wasPendingInvite) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match cannot be cancelled.");
        }

        String fromStatus = match.getStatus().name();
        match.setCompanyStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        if (wasMatched) {
            decrementFilledPositions(match.getProject());
        }
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "COMPANY");
        Match saved = matchRepository.save(match);

        String companyName = match.getProject().getCompany().getCompanyName();
        String projectTitle = match.getProject().getTitle();

        if (wasMatched) {
            notificationService.notifyMatchCancelled(
                match.getProfessional().getUser(), companyName, projectTitle);
            emailService.send(
                match.getProfessional().getUser().getEmail(),
                "Match cancelado — Nexus",
                "Olá " + match.getProfessional().getName() + ",\n\n" +
                companyName + " cancelou o match confirmado para o projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
            );
        } else {
            notificationService.notifyInviteCancelled(
                match.getProfessional().getUser(), companyName, projectTitle);
            emailService.send(
                match.getProfessional().getUser().getEmail(),
                "Convite cancelado — Nexus",
                "Olá " + match.getProfessional().getName() + ",\n\n" +
                companyName + " cancelou o convite enviado para o projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
            );
        }

        return saved;
    }

    @Transactional
    public Match professionalAccepts(Long matchId, Long professionalId) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);

        if (match.getStatus() != StatusMatch.COMPANY_INTERESTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not awaiting a professional response.");
        }
        assertProjectHasOpenPositions(match.getProject());

        String fromStatus = match.getStatus().name();
        match.setProfessionalStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.MATCHED);
        incrementFilledPositions(match.getProject());
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "PROFESSIONAL");
        Match saved = matchRepository.save(match);
        notifyMutualMatch(saved);
        return saved;
    }

    public Match professionalRejectsWithFeedback(Long matchId, Long professionalId, List<ProfessionalRejectionReason> reasons) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);

        String fromStatus = match.getStatus().name();
        match.setProfessionalStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "PROFESSIONAL");
        Match saved = matchRepository.save(match);

        saveProfessionalRejection(match, reasons);
        notificationService.notifyInviteRejected(
            match.getProject().getCompany().getUser(),
            match.getProfessional().getName(),
            match.getProject().getTitle()
        );
        return saved;
    }

    // Ao encerrar um projeto, matches que ainda estavam pendentes (nunca chegaram a ser
    // confirmados) não têm mais como prosseguir. Viram REJECTED, mas sem marcar
    // companyStatus/professionalStatus como REJECTED individualmente — isso é o que
    // diferencia "cancelado por encerramento do projeto" de uma recusa ativa de qualquer
    // lado na hora de exibir a aba de recusados no frontend.
    public void cancelPendingMatchesForClosedProject(Project project) {
        List<Match> pending = matchRepository.findByProjectId(project.getId())
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.WAITING
                          || m.getStatus() == StatusMatch.COMPANY_INTERESTED
                          || m.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED)
                .toList();

        for (Match m : pending) {
            String fromStatus = m.getStatus().name();
            m.setStatus(StatusMatch.REJECTED);
            matchHistoryService.record(m, fromStatus, m.getStatus().name(), "SYSTEM");
            matchRepository.save(m);

            notificationService.notifyProjectClosed(
                    m.getProfessional().getUser(),
                    project.getTitle(),
                    project.getCompany().getCompanyName()
            );
        }
    }

    // validações de posse

    private void validateCompanyOwnership(Match match, Long companyId) {
        if (!match.getProject().getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This match does not belong to your company.");
        }
    }

    private void validateProfessionalOwnership(Match match, Long professionalId) {
        if (!match.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This match does not belong to you.");
        }
    }

    // cancelamento genérico (empresa ou profissional) marca active = fals

    @Transactional
    public Match cancelMatch(Long matchId, Long companyId, Long professionalId, String changedBy) {
        Match match = findById(matchId);

        if (companyId != null) {
            validateCompanyOwnership(match, companyId);
        } else if (professionalId != null) {
            validateProfessionalOwnership(match, professionalId);
        } else {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not authorized to cancel this match.");
        }

        if (match.getStatus() != StatusMatch.MATCHED || Boolean.FALSE.equals(match.getActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match cannot be cancelled.");
        }

        String fromStatus = match.getStatus().name();
        match.setActive(false);
        match.setStatus(StatusMatch.REJECTED);
        decrementFilledPositions(match.getProject());
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), changedBy);
        Match saved = matchRepository.save(match);

        String companyName = match.getProject().getCompany().getCompanyName();
        String professionalName = match.getProfessional().getName();
        String projectTitle = match.getProject().getTitle();

        if ("COMPANY".equals(changedBy)) {
            notificationService.notifyMatchCancelled(
                match.getProfessional().getUser(), companyName, projectTitle);
            emailService.send(
                match.getProfessional().getUser().getEmail(),
                "Match cancelado — Nexus",
                "Olá " + professionalName + ",\n\n" +
                companyName + " cancelou o match confirmado para o projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
            );
        } else {
            notificationService.notifyMatchCancelled(
                match.getProject().getCompany().getUser(), professionalName, projectTitle);
            emailService.send(
                match.getProject().getCompany().getUser().getEmail(),
                "Match cancelado — Nexus",
                "Olá " + companyName + ",\n\n" +
                professionalName + " cancelou o match confirmado para o projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
            );
        }

        return saved;
    }

    // prersistência de feedback de rejeicao, separado por quem rejeita 
    
    private void saveProfessionalRejection(Match match, List<ProfessionalRejectionReason> reasons) {
        RejectionFeedback feedback = new RejectionFeedback();
        feedback.setMatch(match);
        feedback.setRejectedBy(AuthorType.PROFESSIONAL);
        feedback.setProfessionalReasons(reasons);
        rejectionFeedbackRepository.save(feedback);
        reputationService.recalculateForCompany(match.getProject().getCompany().getId());
    }

    private void saveCompanyRejection(Match match, List<CompanyRejectionReason> reasons) {
        RejectionFeedback feedback = new RejectionFeedback();
        feedback.setMatch(match);
        feedback.setRejectedBy(AuthorType.COMPANY);
        feedback.setCompanyReasons(reasons);
        rejectionFeedbackRepository.save(feedback);
        reputationService.recalculateForProfessional(match.getProfessional().getId());
    }

    // PROFISSIONAL — OPORTUNIDADES E INICIATIVA

    public List<Match> getOpportunitiesForProfessional(Long professionalId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));

        List<Project> openProjects = projectRepository.findByStatus(ProjectStatus.OPEN);

        for (Project project : openProjects) {
            boolean alreadyExists = matchRepository
                    .findByProjectIdAndProfessionalId(project.getId(), professionalId)
                    .isPresent();

            if (!alreadyExists && matchesProjectType(professional, project)) {
                Match match = new Match();
                match.setProject(project);
                match.setProfessional(professional);
                match.setMatchScore(getScore(professional, project));
                matchRepository.save(match);
            }
        }

        return matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .filter(m -> m.getProject().getStatus() == ProjectStatus.OPEN)
                .filter(m -> m.getStatus() == StatusMatch.WAITING
                          || m.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED)
                .sorted(Comparator.comparingDouble(Match::getMatchScore).reversed())
                .toList();
    }

    @Transactional
    public Match professionalShowsInterest(Long professionalId, Long projectId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));

        Match match = matchRepository
                .findByProjectIdAndProfessionalId(projectId, professionalId)
                .orElseGet(() -> {
                    Match newMatch = new Match();
                    newMatch.setProject(project);
                    newMatch.setProfessional(professional);
                    newMatch.setMatchScore(getScore(professional, project));
                    newMatch.setInitiatedBy(InitiatedBy.PROFESSIONAL);
                    return newMatch;
                });

        if (match.getStatus() == StatusMatch.REJECTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match was rejected and cannot be reactivated.");
        }

        boolean wasAlreadyCompanyInterested = match.getStatus() == StatusMatch.COMPANY_INTERESTED;
        String fromStatus = match.getStatus().name();

        if (wasAlreadyCompanyInterested) {
            assertProjectHasOpenPositions(match.getProject());
            match.setProfessionalStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.MATCHED);
            incrementFilledPositions(match.getProject());
        } else {
            assertProjectIsOpen(project);
            match.setProfessionalStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.PROFESSIONAL_INTERESTED);
        }

        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "PROFESSIONAL");
        Match saved = matchRepository.save(match);

        if (!wasAlreadyCompanyInterested) {
            notificationService.notifyNewInterestReceived(
                saved.getProject().getCompany().getUser(),
                saved.getProfessional().getName(),
                saved.getProject().getTitle(),
                saved.getId()
            );
        }

        if (wasAlreadyCompanyInterested) {
            notifyMutualMatch(saved);
        }

        return saved;
    }

    // CONSULTAS

    public List<Match> getRankingByProject(Long projectId) {
        return matchRepository.findByProjectId(projectId)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .sorted(Comparator.comparingDouble(Match::getMatchScore).reversed())
                .toList();
    }

    public List<MatchHistory> getHistory(Long matchId) {
        return matchHistoryRepository.findByMatchIdOrderByChangedAtAsc(matchId);
    }

    public List<Match> getRankingByProjectWithFilters(
            Long projectId,
            String city,
            String uf,
            String experienceLevel,
            Boolean available,
            Double minSalary,
            Double maxSalary,
            String skill) {

        List<Match> ranking = getRankingByProject(projectId);

        if (city != null && !city.isBlank()) {
            ranking = ranking.stream()
                    .filter(m -> m.getProfessional().getCity() != null
                            && m.getProfessional().getCity().equalsIgnoreCase(city.trim()))
                    .toList();
        }
        if (uf != null && !uf.isBlank()) {
            ranking = ranking.stream()
                    .filter(m -> m.getProfessional().getUf() != null
                            && m.getProfessional().getUf().equalsIgnoreCase(uf.trim()))
                    .toList();
        }
        if (experienceLevel != null && !experienceLevel.isBlank()) {
            try {
                ExperienceLevel level = ExperienceLevel.valueOf(experienceLevel.trim().toUpperCase());
                ranking = ranking.stream()
                        .filter(m -> m.getProfessional().getExperienceLevel() == level)
                        .toList();
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (available != null) {
            ranking = ranking.stream()
                    .filter(m -> Boolean.valueOf(available).equals(m.getProfessional().getAvailable()))
                    .toList();
        }
        if (minSalary != null) {
            ranking = ranking.stream()
                    .filter(m -> {
                        Double expected = calculateExpectedSalary(m.getProfessional(), m.getProject());
                        return expected != null && expected >= minSalary;
                    })
                    .toList();
        }
        if (maxSalary != null) {
            ranking = ranking.stream()
                    .filter(m -> {
                        Double expected = calculateExpectedSalary(m.getProfessional(), m.getProject());
                        return expected != null && expected <= maxSalary;
                    })
                    .toList();
        }
        if (skill != null && !skill.isBlank()) {
            String skillLower = skill.trim().toLowerCase();
            ranking = ranking.stream()
                    .filter(m -> m.getProfessional().getSkills() != null
                            && m.getProfessional().getSkills().stream()
                                    .anyMatch(s -> s.getName().toLowerCase().contains(skillLower)))
                    .toList();
        }

        return ranking;
    }

    public List<Match> getMatchesByProfessional(Long professionalId) {
        return matchRepository.findByProfessionalId(professionalId);
    }

    public long countConfirmedMatches() {
        return matchRepository.countByStatus(StatusMatch.MATCHED);
    }

    public Match findById(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + id));
    }

    public List<Match> getPendingInvitesForProfessional(Long professionalId) {
        return matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.COMPANY_INTERESTED)
                .toList();
    }

    public List<Match> getConfirmedMatchesForProfessional(Long professionalId) {
        return matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .toList();
    }

    // Verifica se empresa e profissional têm um match confirmado entre eles
    // Usado para liberar dados de contat só depois do match.
    public boolean hasConfirmedMatchBetween(Long companyId, Long professionalId) {
        return matchRepository.findByProfessionalId(professionalId)
                .stream()
                .anyMatch(m -> m.getStatus() == StatusMatch.MATCHED
                        && m.getProject().getCompany().getId().equals(companyId));
    }

    public long countConfirmedMatchesByCompany(Long companyId) {
        return matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .count();
    }

    public List<Match> getMatchesByCompany(Long companyId) {
        return matchRepository.findByProjectCompanyId(companyId);
    }

    public List<Match> getPreviousProjectsByCompany(Long companyId) {
        return matchRepository.findByCompanyIdAndStatusAndActiveFalse(companyId, StatusMatch.MATCHED);
    }

    // NOTIFICAÇÕES

    private void notifyMutualMatch(Match match) {
        String professionalEmail = match.getProfessional().getUser().getEmail();
        String companyEmail = match.getProject().getCompany().getUser().getEmail();
        String professionalName = match.getProfessional().getName();
        String companyName = match.getProject().getCompany().getCompanyName();
        String projectTitle = match.getProject().getTitle();

        notificationService.notifyMatchConfirmed(
            match.getProfessional().getUser(), companyName, projectTitle, match.getId());
        notificationService.notifyMatchConfirmed(
            match.getProject().getCompany().getUser(), professionalName, projectTitle, match.getId());

        emailService.send(
            professionalEmail,
            "Match confirmado! — Nexus",
            "Olá " + professionalName + ",\n\n" +
            companyName + " também demonstrou interesse em você para o projeto \"" + projectTitle + "\".\n\n" +
            "O match foi confirmado e os contatos já estão disponíveis no Nexus.\n\nEquipe Nexus"
        );

        emailService.send(
            companyEmail,
            "Match confirmado! — Nexus",
            "Olá " + companyName + ",\n\n" +
            professionalName + " também demonstrou interesse no projeto \"" + projectTitle + "\".\n\n" +
            "O match foi confirmado e os contatos já estão disponíveis no Nexus.\n\nEquipe Nexus"
        );
    }
    
    // Barra novo engajamento (convite, interesse ou confirmação) numa oportunidade que não
    // está mais aberta — encerrada ou pausada por ter atingido o limite de vagas.
    private void assertProjectIsOpen(Project project) {
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This opportunity is not open.");
        }
    }

    // Barra a confirmação de mais um match quando o projeto já não tem vagas livres (encerrado,
    // pausado por ter atingido o limite, ou outro match concorrente acabou de preenchê-lo).
    private void assertProjectHasOpenPositions(Project project) {
        assertProjectIsOpen(project);
        if (!project.hasOpenPositions()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This opportunity has reached its position limit.");
        }
    }

    private void incrementFilledPositions(Project project) {
        projectRepository.incrementFilledPositions(project.getId());
        // a query acima é um bulk update — sincroniza o objeto em memória pra que a checagem
        // de limite logo abaixo (e qualquer leitura subsequente na mesma transação) veja o valor atual
        project.setFilledPositions(project.getFilledPositions() + 1);
        pauseIfPositionsFull(project);
    }

    private void decrementFilledPositions(Project project) {
        projectRepository.decrementFilledPositions(project.getId());
        project.setFilledPositions(Math.max(0, project.getFilledPositions() - 1));
    }

    // Ao preencher a última vaga, pausa o projeto automaticamente: ele para de gerar ranking
    // e some do feed de profissionais até a empresa decidir encerrar ou reabrir com mais vagas.
    // Público também porque ProjectService.reopenProject reusa essa checagem: reabrir um projeto
    // fechado sem aumentar o limite de vagas não pode deixá-lo "OPEN" com 0 vagas reais.
    public void pauseIfPositionsFull(Project project) {
        if (project.getStatus() != ProjectStatus.OPEN) {
            return;
        }
        if (project.getFilledPositions() < project.getMaxPositions()) {
            return;
        }

        project.setStatus(ProjectStatus.PAUSED);
        projectRepository.save(project);

        Company company = project.getCompany();
        notificationService.notifyProjectPositionsFull(
                company.getUser(), project.getTitle(), project.getId());

        emailService.send(
                company.getUser().getEmail(),
                "Limite de vagas atingido — Nexus",
                "Olá " + company.getCompanyName() + ",\n\n" +
                "O projeto \"" + project.getTitle() + "\" atingiu o limite de " + project.getMaxPositions() +
                " vaga(s) e foi pausado automaticamente. Ele não aparecerá mais para novos profissionais " +
                "até que você encerre a oportunidade ou reabra com mais vagas.\n\n" +
                "Acesse o Nexus em Meus Projetos para decidir.\n\nEquipe Nexus"
        );
    }
}