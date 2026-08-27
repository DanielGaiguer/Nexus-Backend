package com.main.nexus.service;

import com.main.nexus.dto.MatchStatusDTO;
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
import com.main.nexus.model.enums.PendingIntentType;
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
import java.util.Set;
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

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

    // SCORE ENGINE — fórmula principal
    // ScoreMatch = (Skills*0.39) + (Budget*0.28) + (History*0.22) + (Reputation*0.11)
    // ou, para ONSITE/HYBRID:
    // ScoreMatch = (Skills*0.33) + (Budget*0.22) + (History*0.19) + (Reputation*0.10) + (Distance*0.16)
    //
    // Obs.: o componente "Disponibilidade" foi removido daqui — como getScore()
    // já retorna 0.0 de cara para profissional indisponível (linha abaixo),
    // calculateAvailabilityScore() sempre valia 100 nesse ponto, ou seja, era
    // uma constante disfarçada de peso variável. O peso que ela ocupava foi
    // redistribuído proporcionalmente entre os demais componentes de cada
    // cenário, mantendo o score de qualquer profissional disponível idêntico
    // ao que já era exibido antes da mudança (ver RN65).
    public double getScore(Professional professional, Project project) {

        // Indisponível anula o score
        if (!Boolean.TRUE.equals(professional.getAvailable())) {
            return 0.0;
        }

        double skillScore      = calculateSkillScore(professional, project);
        double budgetScore     = calculateBudgetScore(professional, project);
        double historyScore    = calculateHistoryScore(professional);
        double reputationScore = calculateReputationScore(professional);

        boolean considersDistance   = project.getWorkMode() == Modality.ONSITE
                                    || project.getWorkMode() == Modality.HYBRID;
        boolean considersExperience = project.getExperienceLevel() != null;

        double baseScore;

        if (considersDistance && considersExperience) {
            // ONSITE/HYBRID com experiência (6 componentes)
            double distanceScore   = calculateDistanceScore(professional, project);
            double experienceScore = calculateExperienceScore(professional, project);

            baseScore = (skillScore      * 0.28)
                      + (budgetScore     * 0.20)
                      + (historyScore    * 0.16)
                      + (reputationScore * 0.09)
                      + (distanceScore   * 0.14)
                      + (experienceScore * 0.13);

        } else if (considersDistance) {
            // ONSITE/HYBRID sem experiência (5 componentes)
            double distanceScore = calculateDistanceScore(professional, project);

            baseScore = (skillScore      * 0.33)
                      + (budgetScore     * 0.22)
                      + (historyScore    * 0.19)
                      + (reputationScore * 0.10)
                      + (distanceScore   * 0.16);

        } else if (considersExperience) {
            // REMOTO com experiência (5 componentes)
            double experienceScore = calculateExperienceScore(professional, project);

            baseScore = (skillScore      * 0.33)
                      + (budgetScore     * 0.24)
                      + (historyScore    * 0.20)
                      + (reputationScore * 0.10)
                      + (experienceScore * 0.13);

        } else {
            // REMOTO sem experiência (4 componentes)
            baseScore = (skillScore      * 0.39)
                      + (budgetScore     * 0.28)
                      + (historyScore    * 0.22)
                      + (reputationScore * 0.11);
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

        double skillScore      = calculateSkillScore(professional, project);
        double budgetScore     = calculateBudgetScore(professional, project);
        double historyScore    = calculateHistoryScore(professional);
        double reputationScore = calculateReputationScore(professional);
        // availabilityScore não entra mais no cálculo do baseScore (ver nota em
        // getScore()); mantido aqui apenas para exibição informativa no
        // comparativo de candidatos, conforme RN88.
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
                distanceScore, experienceScore,
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

    // Breakdown "vivo" pra telas que exibem um candidato/profissional sem necessariamente ter um
    // Match persistido por trás ainda (proposta sem match bilateral, processo seletivo) -- mesmo
    // padrão de resolução do ScorePreviewService.buildPreview: reaproveita o Match existente (com
    // o score atualizado) ou monta um transiente só pra calcular o breakdown, sem salvar nada.
    public ScoreBreakdownDTO getScoreBreakdownForCandidate(Long professionalId, Long projectId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));

        Match match = matchRepository.findByProjectIdAndProfessionalId(projectId, professionalId)
                .map(existing -> {
                    refreshMatchScore(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    Match transientMatch = new Match();
                    transientMatch.setProject(project);
                    transientMatch.setProfessional(professional);
                    transientMatch.setMatchScore(getScore(professional, project));
                    return transientMatch;
                });

        return getScoreBreakdown(professional, project, match);
    }

    // Mesma fórmula de ponderação usada em getScore(), mas sem o multiplicador
    // de reputação, e juntado o base score para poder mostrar o quanto
    // desse multiplicador contribuiu (reputationAdjustment) separadamente.
    private double computeBaseScoreForBreakdown(
            double skill, double budget, double history,
            double reputation,
            Double distance, Double experience,
            boolean considersDistance, boolean considersExperience) {

        if (considersDistance && considersExperience) {
            return (skill * 0.28) + (budget * 0.20) + (history * 0.16)
                 + (reputation * 0.09)
                 + (distance   * 0.14) + (experience  * 0.13);
        } else if (considersDistance) {
            return (skill * 0.33) + (budget * 0.22) + (history * 0.19)
                 + (reputation * 0.10)
                 + (distance * 0.16);
        } else if (considersExperience) {
            return (skill * 0.33) + (budget * 0.24) + (history * 0.20)
                 + (reputation * 0.10)
                 + (experience * 0.13);
        } else {
            return (skill * 0.39) + (budget * 0.28) + (history * 0.22)
                 + (reputation * 0.11);
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

    // Esse e o metodo que gera  o ranking inicial de candidatos para um projeto, criando um Match (com score) para cada profissional
    //elegível que ainda não tem match com aquele projeto
    //varre a base de profissionais, filtra quem é elegível e ainda não tem match, 
    //calcula o score de cada um, notifica os casos de match muito forte, e grava o ranking ordenado no banco. 
    //É chamado na criação do projeto e, indiretamente, dentro de recalculateRankingForProject 
    //(que primeiro recalcula os scores dos matches já existentes em WAITING, depois chama generateRankingForProject 
    //de novo pra pegar profissionais novos que ainda não tinham match)
    public List<Match> generateRankingForProject(Project project) {
        if (project.getStatus() != ProjectStatus.OPEN) {
            return List.of(); //Se o projeto nao esta OPEN, retorna uma lista vazia
        }
        
        //Essa busca ampla pode se tornar um gargalo de performance à medida que a base de profissionais cresce 
        //uma otimização futura óbvia seria filtrar por available = true e/ou por região direto na query SQL
        List<Professional> allProfessionals = professionalRepository.findAll();
        List<Match> matches = new ArrayList<>();

        for (Professional professional : allProfessionals) {
            // Profissional indisponível não entra no ranking — nem como candidato gerado
            // automaticamente. Ver getScore(), que também zera o score caso esse match já
            // exista de antes (ex: ficou indisponível depois de já ter sido rankeado).
            if (!Boolean.TRUE.equals(professional.getAvailable())) continue;

            // Verifica se o profissional Tem o minimo indispensavel para o calculo do score
            if (!profileCompletionService.canParticipateInRanking(professional)) continue;

            //Se nao for do mesmo tipo de oportunidade
            if (!matchesProjectType(professional, project)) continue;

            // Verifica se ja existe
            boolean alreadyExists = matchRepository
                    .findByProjectIdAndProfessionalId(project.getId(), professional.getId())
                    .isPresent(); // Se esta presente, se nao esta vazio
            if (alreadyExists) continue; // Se ja existe, nao gera o ranking

            //Gera o score do profissional para tal projeto
            double score = getScore(professional, project);

            Match match = new Match();
            match.setProject(project);
            match.setProfessional(professional);
            match.setMatchScore(score);
            //Adiciona a lista de matches
            matches.add(match);

            // Alerta de alta compatibilidade - e-mail + notificação in-app para o profissional, e e-mail + notificação in-app para a empresa
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
        
        // .sort = Ordena os elementos da propria lista
        // O Comparator é um objeto que diz como comparar dois elementos.
        // Comparator.comparingDouble(...), Esse método cria um comparador baseado em um valor double
        // Compare dois objetos Match usando o valor retornado por getMatchScore()
        // Por padrão, Comparator.comparingDouble ordena do menor para o maior
        // Reversed, faz ordenar as listas de maneira decrescente, o que faz sentido, primeiro vem os matches com maior score
        matches.sort(Comparator.comparingDouble(Match::getMatchScore).reversed());
        //Salva Todos os matches
        return matchRepository.saveAll(matches);
    }

    //Chamado quando uma oportunidade e editada ou reaberta
    @Transactional
    public void recalculateRankingForProject(Project project) {
        //Pega todos os matches ja existentes daquele projeto
        List<Match> existingMatches = matchRepository.findByProjectId(project.getId());

        //
        for (Match match : existingMatches) {
            //Recalcula apenas matches que estao em WAITING, nao faz sentido recalcular matches ja feitos ou rejeitados
            if (match.getStatus() == StatusMatch.WAITING) {
                //Vai calcular o novo score do profissional com o projeto
                double newScore = getScore(match.getProfessional(), project);
                //Atualiza o score daquele match
                match.setMatchScore(newScore);
            }
        }
        //Salva os matches atualizados
        matchRepository.saveAll(existingMatches);

        
        // graças à checagem alreadyExists, só vai efetivamente adicionar matches novos 
        // (novos profissionais que se tornaram elegíveis desde a última geração, por exemplo por completarem o perfil), 
        // sem duplicar os existentes
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
        // Valida se aquela empresa e dona
        validateCompanyOwnership(match, companyId);
        return applyCompanyInterest(match);
    }

    // Mesma ideia de professionalShowsInterest, do lado da empresa: usado quando a
    // empresa demonstra interesse num profissional achado pelo diretório geral
    // (/company/professionals), não pelo ranking de um projeto -- pode não existir
    // match nenhum ainda entre os dois, então localiza ou cria um (nasce WAITING,
    // ver Match#status) antes de aplicar o mesmo fluxo de companyShowsInterest.
    @Transactional
    public Match companyShowsInterestByProject(Long professionalId, Long projectId, Long companyId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));

        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }

        Match match = matchRepository
                .findByProjectIdAndProfessionalId(projectId, professionalId)
                .orElseGet(() -> {
                    Match newMatch = new Match();
                    newMatch.setProject(project);
                    newMatch.setProfessional(professional);
                    newMatch.setMatchScore(getScore(professional, project));
                    newMatch.setInitiatedBy(InitiatedBy.COMPANY);
                    return newMatch;
                });

        return applyCompanyInterest(match);
    }

    // Status do match (se existir) entre um profissional e um projeto -- usado
    // pelos diálogos de comparação (ProfessionalCompareDialog) pra decidir se
    // mostram "Demonstrar interesse": só faz sentido quando ainda não há
    // nenhum envolvimento (status null ou WAITING), não quando já está em
    // andamento (COMPANY_INTERESTED/PROFESSIONAL_INTERESTED/MATCHED) ou
    // recusado.
    public MatchStatusDTO getStatusByPair(Long professionalId, Long projectId, Long companyId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found."));

        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }

        return matchRepository.findByProjectIdAndProfessionalId(projectId, professionalId)
                .map(m -> new MatchStatusDTO(m.getStatus()))
                .orElseGet(() -> new MatchStatusDTO(null));
    }

    // Núcleo compartilhado por companyShowsInterest e companyShowsInterestByProject
    // -- a única diferença entre os dois é como o Match chega até aqui (por id já
    // existente vs. localizado/criado a partir de profissional+projeto).
    private Match applyCompanyInterest(Match match) {
        if (match.getStatus() == StatusMatch.REJECTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match was rejected and cannot be reactivated.");
        }

        // Verifica se o profisional ja demonstrou interesse no match
        boolean wasAlreadyProfessionalInterested = match.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED;
        String fromStatus = match.getStatus().name();

        if (wasAlreadyProfessionalInterested) {
            // Verifica se o projeto tem posicoes disponiveis
            assertProjectHasOpenPositions(match.getProject());
            // Atualiza o Status para empresa interessada
            match.setCompanyStatus(InterestStatus.INTERESTED);
            // Atualiza o status do match para matched
            match.setStatus(StatusMatch.MATCHED);
            // Incrementa uma unidade nas posicoes
            incrementFilledPositions(match.getProject());
        } else {
            // Valida se o projeto esta aberto
            assertProjectIsOpen(match.getProject());
            //Adiciona a empresa interessada no status
            match.setCompanyStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.COMPANY_INTERESTED);
            // Adiciona a empresa como iniciadora do match
            match.setInitiatedBy(InitiatedBy.COMPANY);
        }

        // Salva o Match primeiro -- precisa ter um id antes do histórico poder referenciá-lo.
        // Só importa de verdade quando é a primeira vez entre esse par (match ainda não existia,
        // criado agora em companyShowsInterestByProject): salvar o histórico antes do match em
        // si fazia o Hibernate recusar (TransientPropertyValueException), já que MatchHistory
        // apontava pra um Match sem id ainda.
        Match saved = matchRepository.save(match);
        // Esse metodo salva o historico do match, para popular uma linha do tempo
        matchHistoryService.record(saved, fromStatus, saved.getStatus().name(), "COMPANY");

        // Se ja tem um profissional interessado, manda notificacao para ambos
        if (wasAlreadyProfessionalInterested) {
            notifyMutualMatch(saved);
        } else {
            // Se nao, envia notificacao e email de novo convite para o profissional
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

        // Retorna o match salvo
        return saved;
    }

    // E a confirmacao explicita de convite
    @Transactional
    public Match companyAccepts(Long matchId, Long companyId) {
        Match match = findById(matchId);
        // valida se a empresa e dona do projeto e do match
        validateCompanyOwnership(match, companyId);

        if (match.getStatus() != StatusMatch.PROFESSIONAL_INTERESTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not awaiting a company response.");
        }
        // Verifica se o projeto esta aberto e se te, posicoes disponiveis
        assertProjectHasOpenPositions(match.getProject());

        // Pega o status anterior
        String fromStatus = match.getStatus().name();
        //Marca a empresa como interessada
        match.setCompanyStatus(InterestStatus.INTERESTED);
        // Marca o status como matched
        match.setStatus(StatusMatch.MATCHED);
        //Adiciona uma unidade nas posicoes disponiveis
        incrementFilledPositions(match.getProject());
        // Salva o historico do match
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "COMPANY");
        //Salva o match atualizado
        Match saved = matchRepository.save(match);
        // Notifica ambos os lados que o match foi aceito
        notifyMutualMatch(saved);
        // Retorna o match atualizado
        return saved;
    }

    @Transactional
    public Match companyRejectsWithFeedback(
            Long matchId, Long companyId, List<CompanyRejectionReason> reasons, String description) {
        Match match = findById(matchId);
        //Valida se empresa e realmente dona
        validateCompanyOwnership(match, companyId);

        if (reasons == null || reasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "At least one rejection reason must be provided.");
        }

        //Salva o historico e atualiza o status do match para rejeitado
        String fromStatus = match.getStatus().name();
        match.setCompanyStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "COMPANY");
        Match saved = matchRepository.save(match);
        screeningInvitationService.cancelPendingForProfessionalProject(saved.getProject(), saved.getProfessional());

        // Salva a rejeicao, com os devidos motivos dela, e notifica o profissional da rejeicao
        saveCompanyRejection(match, reasons, description);
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
        return cancelMatchInternal(match, true, "COMPANY");
    }

    // Espelho de companyCancelsMatch: profissional retira um interesse pendente
    // (PROFESSIONAL_INTERESTED, ainda não respondido pela empresa) ou cancela um match já confirmado (MATCHED).
    @Transactional
    public Match professionalCancelsMatch(Long matchId, Long professionalId) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);
        return cancelMatchInternal(match, false, "PROFESSIONAL");
    }

    //Espelho exato do companyAccepts -- exceto pelo gate do questionário de triagem (ver
    //ScreeningInvitationService), que pode segurar a confirmação até o profissional responder
    @Transactional
    public MatchActionResult professionalAccepts(Long matchId, Long professionalId) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);

        if (match.getStatus() != StatusMatch.COMPANY_INTERESTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not awaiting a professional response.");
        }
        assertProjectHasOpenPositions(match.getProject());

        ScreeningInvitationService.GateResult gate = screeningInvitationService.checkGate(
                match.getProject(), match.getProfessional(), PendingIntentType.MATCH_ACCEPT, match, null);
        if (gate.blocked()) {
            return new MatchActionResult(match, true, gate.invitationId());
        }

        return new MatchActionResult(applyProfessionalAccept(match, true), false, null);
    }

    // Núcleo que confirma o match -- chamado na hora (gate passou, precondições já validadas em
    // professionalAccepts) ou depois, retomado por ScreeningInvitationController.submit quando o
    // profissional termina o questionário obrigatório (ver PendingIntentType.MATCH_ACCEPT).
    // `strict` diferencia as duas formas de chegar aqui: true replica exatamente o
    // comportamento de sempre (lança erro se o estado não permitir); false é best-effort --
    // usado só na retomada, quando o estado pode ter mudado enquanto o profissional respondia
    // (ex.: empresa cancelou o convite nesse meio tempo) e não faz sentido falhar a submissão
    // do questionário por causa disso.
    @Transactional
    public Match applyProfessionalAccept(Match match, boolean strict) {
        if (match.getStatus() != StatusMatch.COMPANY_INTERESTED) {
            if (strict) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "This match is not awaiting a professional response.");
            }
            return match;
        }
        if (strict) {
            assertProjectHasOpenPositions(match.getProject());
        } else if (!match.getProject().hasOpenPositions()) {
            return match;
        }

        String fromStatus = match.getStatus().name();
        match.setProfessionalStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.MATCHED);
        incrementFilledPositions(match.getProject());
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "PROFESSIONAL");
        Match saved = matchRepository.save(match);
        notifyMutualMatch(saved);
        return saved;
    }

    @Transactional
    //Exatamente o mesmo fluxo de rejeicao por parte de company
    public Match professionalRejectsWithFeedback(
            Long matchId, Long professionalId, List<ProfessionalRejectionReason> reasons, String description) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);

        if (reasons == null || reasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "At least one rejection reason must be provided.");
        }

        String fromStatus = match.getStatus().name();
        match.setProfessionalStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), "PROFESSIONAL");
        Match saved = matchRepository.save(match);
        screeningInvitationService.cancelPendingForProfessionalProject(saved.getProject(), saved.getProfessional());

        saveProfessionalRejection(match, reasons, description);
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

        // Cobre também profissionais que ainda estavam no meio do questionário de triagem sem
        // nem ter chegado a ter um Match (ex.: veio pelo gate de enviar proposta) -- por isso é
        // uma varredura à parte do loop acima, não por-match.
        screeningInvitationService.cancelAllPendingForProject(project);
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

    // Cancelamento genérico (empresa ou profissional, decidido por qual id foi passado) —
    // agora só um roteador fino para cancelMatchInternal, o mesmo núcleo usado por
    // companyCancelsMatch/professionalCancelsMatch. Antes essa era uma implementação própria
    // que só marcava `active=false` (sem tocar em companyStatus/professionalStatus) e só
    // aceitava status MATCHED — divergia de companyCancelsMatch, que marcava
    // companyStatus=REJECTED (sem tocar em `active`) e também aceitava convites pendentes
    // (COMPANY_INTERESTED). O mesmo cancelamento produzia resultados diferentes no banco
    // dependendo de qual endpoint fosse chamado. Delegar para cancelMatchInternal elimina
    // essa divergência.
    @Transactional
    public Match cancelMatch(Long matchId, Long companyId, Long professionalId, String changedBy) {
        Match match = findById(matchId);

        if (companyId != null) {
            validateCompanyOwnership(match, companyId);
            return cancelMatchInternal(match, true, changedBy);
        } else if (professionalId != null) {
            validateProfessionalOwnership(match, professionalId);
            return cancelMatchInternal(match, false, changedBy);
        }

        throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                "You are not authorized to cancel this match.");
    }

    // Núcleo único de cancelamento, compartilhado por companyCancelsMatch,
    // professionalCancelsMatch e pelo cancelMatch genérico. Permite cancelar um match já
    // confirmado (MATCHED, de qualquer lado) ou retirar um convite/interesse pendente do
    // próprio lado que está cancelando (COMPANY_INTERESTED para empresa,
    // PROFESSIONAL_INTERESTED para profissional). Sempre marca `active=false` e o
    // status de interesse (companyStatus/professionalStatus) do lado que cancelou como REJECTED juntos 
    private Match cancelMatchInternal(Match match, boolean isCompanySide, String changedBy) {
        boolean wasMatched = match.getStatus() == StatusMatch.MATCHED;
        boolean wasPendingOnThisSide = isCompanySide
                ? match.getStatus() == StatusMatch.COMPANY_INTERESTED
                : match.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED;

        if (!wasMatched && !wasPendingOnThisSide) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match cannot be cancelled.");
        }

        String fromStatus = match.getStatus().name();
        if (isCompanySide) {
            match.setCompanyStatus(InterestStatus.REJECTED);
        } else {
            match.setProfessionalStatus(InterestStatus.REJECTED);
        }
        match.setStatus(StatusMatch.REJECTED);
        match.setActive(false);
        if (wasMatched) {
            //Limpa a posicao para outra oportunidade
            decrementFilledPositions(match.getProject());
        }
        matchHistoryService.record(match, fromStatus, match.getStatus().name(), changedBy);
        Match saved = matchRepository.save(match);
        screeningInvitationService.cancelPendingForProfessionalProject(saved.getProject(), saved.getProfessional());

        notifyCancellation(saved, isCompanySide, wasMatched);
        return saved;
    }

    // Avisa o outro lado do cancelamento — texto varia conforme o match já estava
    // confirmado (MATCHED) ou ainda era só um convite/interesse pendente.
    private void notifyCancellation(Match match, boolean isCompanySide, boolean wasMatched) {
        String companyName = match.getProject().getCompany().getCompanyName();
        String professionalName = match.getProfessional().getName();
        String projectTitle = match.getProject().getTitle();

        if (isCompanySide) {
            if (wasMatched) {
                notificationService.notifyMatchCancelled(
                    match.getProfessional().getUser(), companyName, projectTitle);
                emailService.send(
                    match.getProfessional().getUser().getEmail(),
                    "Match cancelado — Nexus",
                    "Olá " + professionalName + ",\n\n" +
                    companyName + " cancelou o match confirmado para o projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
                );
            } else {
                notificationService.notifyInviteCancelled(
                    match.getProfessional().getUser(), companyName, projectTitle);
                emailService.send(
                    match.getProfessional().getUser().getEmail(),
                    "Convite cancelado — Nexus",
                    "Olá " + professionalName + ",\n\n" +
                    companyName + " cancelou o convite enviado para o projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
                );
            }
        } else {
            if (wasMatched) {
                notificationService.notifyMatchCancelled(
                    match.getProject().getCompany().getUser(), professionalName, projectTitle);
                emailService.send(
                    match.getProject().getCompany().getUser().getEmail(),
                    "Match cancelado — Nexus",
                    "Olá " + companyName + ",\n\n" +
                    professionalName + " cancelou o match confirmado para o projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
                );
            } else {
                notificationService.notifyInterestWithdrawn(
                    match.getProject().getCompany().getUser(), professionalName, projectTitle);
                emailService.send(
                    match.getProject().getCompany().getUser().getEmail(),
                    "Interesse retirado — Nexus",
                    "Olá " + companyName + ",\n\n" +
                    professionalName + " retirou o interesse demonstrado no projeto \"" + projectTitle + "\".\n\nEquipe Nexus"
                );
            }
        }
    }

    // prersistência de feedback de rejeicao, separado por quem rejeita 
    
    private void saveProfessionalRejection(Match match, List<ProfessionalRejectionReason> reasons, String description) {
        RejectionFeedback feedback = new RejectionFeedback();
        feedback.setMatch(match);
        feedback.setRejectedBy(AuthorType.PROFESSIONAL);
        feedback.setProfessionalReasons(reasons);
        feedback.setDescription(description);
        //Recalcula a reputacao depois de rejeitado
        rejectionFeedbackRepository.save(feedback);
        reputationService.recalculateForCompany(match.getProject().getCompany().getId());
    }

    private void saveCompanyRejection(Match match, List<CompanyRejectionReason> reasons, String description) {
        RejectionFeedback feedback = new RejectionFeedback();
        feedback.setMatch(match);
        feedback.setRejectedBy(AuthorType.COMPANY);
        feedback.setCompanyReasons(reasons);
        feedback.setDescription(description);
        //Recalcula a reputacao depois de rejeitado
        rejectionFeedbackRepository.save(feedback);
        reputationService.recalculateForProfessional(match.getProfessional().getId());
    }

    // Usado pelas telas de "Recusados" pra mostrar motivos + observação no card do match.
    public RejectionFeedback getRejectionFeedback(Long matchId) {
        return rejectionFeedbackRepository.findByMatchId(matchId).orElse(null);
    }

    // Nomes brutos (do enum) dos motivos selecionados na rejeição — só um dos dois lados
    // (companyReasons/professionalReasons) vem preenchido por vez, dependendo de quem
    // rejeitou. O front traduz esses nomes pra rótulo em português.
    public List<String> getRejectionReasonNames(RejectionFeedback feedback) {
        if (feedback == null) return null;
        if (feedback.getCompanyReasons() != null && !feedback.getCompanyReasons().isEmpty()) {
            return feedback.getCompanyReasons().stream().map(Enum::name).toList();
        }
        if (feedback.getProfessionalReasons() != null && !feedback.getProfessionalReasons().isEmpty()) {
            return feedback.getProfessionalReasons().stream().map(Enum::name).toList();
        }
        return List.of();
    }

    // PROFISSIONAL — OPORTUNIDADES E INICIATIVA

    // Gera os matches (status WAITING) que faltam pro profissional em toda vaga OPEN
    // elegível — mesma lógica usada tanto na navegação lazy (getOpportunitiesForProfessional)
    // quanto no recálculo imediato disparado ao marcar disponível de novo
    // (recalculateForNewlyAvailableProfessional). Indisponível nunca gera nada aqui.
    private void generateMissingMatchesForProfessional(Professional professional) {
        if (!Boolean.TRUE.equals(professional.getAvailable())) {
            return;
        }

        List<Project> openProjects = projectRepository.findByStatus(ProjectStatus.OPEN);

        for (Project project : openProjects) {
            boolean alreadyExists = matchRepository
                    .findByProjectIdAndProfessionalId(project.getId(), professional.getId())
                    .isPresent();

            if (!alreadyExists && matchesProjectType(professional, project)) {
                Match match = new Match();
                match.setProject(project);
                match.setProfessional(professional);
                match.setMatchScore(getScore(professional, project));
                matchRepository.save(match);
            }
        }
    }

    // Chamado pelo endpoint de atualização de perfil assim que o profissional volta a
    // ficar disponível (false -> true): gera na hora os matches em todas as vagas abertas
    // elegíveis, em vez de esperar ele visitar /pro/opportunities pra isso acontecer.
    // Também recalcula os matches que já existiam de antes (ficaram com score 0 zerado
    // enquanto ele estava indisponível — ver getScore) pra já saírem com o score certo.
    @Transactional
    public void recalculateForNewlyAvailableProfessional(Long professionalId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));

        generateMissingMatchesForProfessional(professional);

        matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .filter(m -> m.getProject().getStatus() == ProjectStatus.OPEN)
                .forEach(this::refreshMatchScore);
    }

    @Transactional
    public List<Match> getOpportunitiesForProfessional(Long professionalId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));

        generateMissingMatchesForProfessional(professional);

        List<Match> matches = matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .filter(m -> m.getProject().getStatus() == ProjectStatus.OPEN)
                .filter(m -> m.getStatus() == StatusMatch.WAITING
                          || m.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED)
                .toList();

        matches.forEach(this::refreshMatchScore);

        return matches.stream()
                .sorted(Comparator.comparingDouble(Match::getMatchScore).reversed())
                .toList();
    }

    // Recalcula o score do match com os dados atuais do profissional/projeto e persiste
    // se tiver mudado — mantém match.getMatchScore() em sincronia com o breakdown ao vivo
    // (getScoreBreakdown), que é sempre recalculado na hora de exibir.
    // Package-private (não private) porque ScorePreviewService e CandidateComparisonService,
    // no mesmo pacote, têm o mesmo problema de reaproveitar um Match já persistido sem
    // atualizar o score antes de montar o breakdown.
    void refreshMatchScore(Match match) {
        double freshScore = getScore(match.getProfessional(), match.getProject());
        if (match.getMatchScore() == null || Math.abs(match.getMatchScore() - freshScore) > 0.01) {
            match.setMatchScore(freshScore);
            matchRepository.save(match);
        }
    }

    
    // Mesmo fluxo de companyShowsInterest -- exceto pelo gate do questionário de triagem (ver
    // ScreeningInvitationService), que pode segurar a confirmação até o profissional responder
    @Transactional
    public MatchActionResult professionalShowsInterest(Long professionalId, Long projectId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));

        Match match = matchRepository
                .findByProjectIdAndProfessionalId(projectId, professionalId)
                .orElseGet(() -> {
                    // Caso ele nao exista, vai criar um match
                    Match newMatch = new Match();
                    newMatch.setProject(project);
                    newMatch.setProfessional(professional);
                    // Gera o score do match
                    newMatch.setMatchScore(getScore(professional, project));
                    // Por quem foi iniciado
                    newMatch.setInitiatedBy(InitiatedBy.PROFESSIONAL);
                    return newMatch;
                });

        if (match.getStatus() == StatusMatch.REJECTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match was rejected and cannot be reactivated.");
        }

        // Precisa de um id salvo antes do gate poder referenciá-lo como pendingMatch (só
        // relevante pra match recém-criado, ainda sem id -- o orElseGet acima não persiste).
        if (match.getId() == null) {
            match = matchRepository.save(match);
        }

        ScreeningInvitationService.GateResult gate = screeningInvitationService.checkGate(
                project, professional, PendingIntentType.MATCH_INTEREST, match, null);
        if (gate.blocked()) {
            return new MatchActionResult(match, true, gate.invitationId());
        }

        return new MatchActionResult(applyProfessionalInterest(match, true), false, null);
    }

    // Núcleo que decide PROFESSIONAL_INTERESTED vs MATCHED (se a empresa já estava
    // interessada) -- chamado na hora (gate passou) ou depois, retomado por
    // ScreeningInvitationController.submit quando o profissional termina o questionário
    // obrigatório (ver PendingIntentType.MATCH_INTEREST). `strict` tem o mesmo papel de
    // applyProfessionalAccept: true replica o comportamento de sempre; false é best-effort,
    // só usado na retomada, e não avança se o match saiu do estado esperado nesse meio tempo.
    @Transactional
    public Match applyProfessionalInterest(Match match, boolean strict) {
        boolean wasAlreadyCompanyInterested = match.getStatus() == StatusMatch.COMPANY_INTERESTED;

        if (!strict) {
            boolean stillActionable = match.getStatus() == StatusMatch.WAITING || wasAlreadyCompanyInterested;
            if (!stillActionable) {
                return match;
            }
        }

        String fromStatus = match.getStatus().name();

        if (wasAlreadyCompanyInterested) {
            // Verifica se o projeto esta aberto e tem posicoes disponiveis
            if (strict) {
                assertProjectHasOpenPositions(match.getProject());
            } else if (!match.getProject().hasOpenPositions()) {
                return match;
            }
            match.setProfessionalStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.MATCHED);
            incrementFilledPositions(match.getProject());
        } else {
            //Verifica se o projeto esta aberto, nao ve as posicoes por que nao gera um match completo
            if (strict) {
                assertProjectIsOpen(match.getProject());
            } else if (match.getProject().getStatus() != ProjectStatus.OPEN) {
                return match;
            }
            match.setProfessionalStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.PROFESSIONAL_INTERESTED);
        }

        Match saved = matchRepository.save(match);
        matchHistoryService.record(saved, fromStatus, saved.getStatus().name(), "PROFESSIONAL");

        if (!wasAlreadyCompanyInterested) {
            notificationService.notifyNewInterestReceived(
                saved.getProject().getCompany().getUser(),
                saved.getProfessional().getName(),
                saved.getProject().getTitle(),
                saved.getId()
            );
            // Retomado depois de aprovar todas as etapas de triagem (strict=false só acontece
            // nesse caminho) -- o profissional não recebe nenhum aviso de "avançou de etapa" pra
            // essa última (não existe próxima), então avisa aqui que o processo terminou e agora
            // a decisão do match é da empresa.
            if (!strict) {
                notificationService.notifyScreeningApprovedAwaitingMatchDecision(
                        saved.getProfessional().getUser(), saved.getProject().getTitle());
                emailService.send(
                        saved.getProfessional().getUser().getEmail(),
                        "Processo seletivo concluído — Nexus",
                        "Olá " + saved.getProfessional().getName() + ",\n\n" +
                        "Você foi aprovado em todas as etapas do processo seletivo do projeto \"" +
                        saved.getProject().getTitle() + "\". A decisão final sobre o match agora é da empresa.\n\n" +
                        "Acesse o Nexus para acompanhar.\n\nEquipe Nexus"
                );
            }
        } else {
            notifyMutualMatch(saved);
        }

        return saved;
    }

    // CONSULTAS

    @Transactional
    public List<Match> getRankingByProject(Long projectId) {
        List<Match> ranking = matchRepository.findByProjectId(projectId)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .toList();

        ranking.forEach(this::refreshMatchScore);

        return ranking.stream()
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

    @Transactional
    public List<Match> getMatchesByProfessional(Long professionalId) {
        List<Match> matches = matchRepository.findByProfessionalId(professionalId);
        // Base de várias telas (dashboard, "Matches Confirmados"/"Recusados" filtrados no
        // controller, admin) — atualiza aqui pra nenhuma delas herdar score congelado.
        matches.forEach(this::refreshMatchScore);
        return matches;
    }

    public long countConfirmedMatches() {
        return matchRepository.countByStatus(StatusMatch.MATCHED);
    }

    public Match findById(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + id));
    }

    @Transactional
    public List<Match> getPendingInvitesForProfessional(Long professionalId) {
        List<Match> invites = matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.COMPANY_INTERESTED)
                .filter(this::isActionablePending)
                .toList();

        // Convite que o profissional já começou a responder (etapa de triagem em andamento) sai
        // daqui e vai pra getInScreeningMatchesForProfessional -- ele não pode simplesmente
        // aceitar/recusar esse convite agora, só terminar de responder.
        Set<Long> inScreening = activeScreeningMatchIds(invites);
        List<Match> result = invites.stream().filter(m -> !inScreening.contains(m.getId())).toList();

        result.forEach(this::refreshMatchScore);

        return result;
    }

    // "Em processo" (profissional): convites/interesses ainda sem decisão final da empresa, mas
    // que já têm um processo seletivo em andamento por trás -- hoje ficariam invisíveis (WAITING
    // nunca aparece em nenhuma aba) ou misturados com convites que ainda dão pra aceitar/recusar
    // direto (COMPANY_INTERESTED). Espelho de getInScreeningMatchesForCompany.
    @Transactional
    public List<Match> getInScreeningMatchesForProfessional(Long professionalId) {
        List<Match> candidates = matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.WAITING
                          || m.getStatus() == StatusMatch.COMPANY_INTERESTED)
                .filter(this::isActionablePending)
                .toList();

        Set<Long> inScreening = activeScreeningMatchIds(candidates);
        List<Match> result = candidates.stream().filter(m -> inScreening.contains(m.getId())).toList();

        result.forEach(this::refreshMatchScore);

        return result;
    }

    // Um convite/interesse só continua "pendente" (exigindo resposta) se a vaga ainda
    // estiver aberta e o match não tiver sido desativado por algum outro motivo — vaga
    // pausada/encerrada ou match inativo tornam a ação (aceitar/recusar) sem sentido, e
    // o convite não deveria continuar aparecendo como se estivesse esperando resposta.
    private boolean isActionablePending(Match m) {
        return !Boolean.FALSE.equals(m.getActive())
                && m.getProject() != null
                && m.getProject().getStatus() == ProjectStatus.OPEN;
    }

    // Dentre os matches informados, quais têm um processo seletivo em andamento por trás (ver
    // ScreeningInvitationService.findMatchIdsWithActiveScreening) -- usado pelas abas "Em
    // processo" dos dois lados, e pra excluir esses matches das abas normais de convite/interesse
    // pendente (evita o mesmo match aparecer em duas abas ao mesmo tempo).
    private Set<Long> activeScreeningMatchIds(List<Match> matches) {
        return screeningInvitationService.findMatchIdsWithActiveScreening(
                matches.stream().map(Match::getId).toList());
    }

    // Espelho de getPendingInvitesForProfessional: interesses que o próprio profissional
    // enviou (professionalShowsInterest) e que ainda aguardam resposta da empresa.
    // Equivalente ao "Convites Enviados" (sentInvites/COMPANY_INTERESTED) que já existe
    // do lado da empresa em getMatchesByCompany + filtro no frontend.
    @Transactional
    public List<Match> getSentInterestsForProfessional(Long professionalId) {
        List<Match> sent = matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED)
                .filter(this::isActionablePending)
                .toList();

        sent.forEach(this::refreshMatchScore);

        return sent;
    }

    @Transactional
    public List<Match> getConfirmedMatchesForProfessional(Long professionalId) {
        List<Match> confirmed = matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .toList();

        confirmed.forEach(this::refreshMatchScore);

        return confirmed;
    }

    // Verifica se empresa e profissional têm um match confirmado E ATIVO entre eles
    // Usado para liberar dados de contato e download de currículo — um match
    // desativado (expirado/encerrado) não deve manter esse acesso liberado.
    public boolean hasConfirmedMatchBetween(Long companyId, Long professionalId) {
        return matchRepository.findByProfessionalId(professionalId)
                .stream()
                .anyMatch(m -> m.getStatus() == StatusMatch.MATCHED
                        && !Boolean.FALSE.equals(m.getActive())
                        && m.getProject().getCompany().getId().equals(companyId));
    }

    public long countConfirmedMatchesByCompany(Long companyId) {
        return matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .count();
    }

    @Transactional
    public List<Match> getMatchesByCompany(Long companyId) {
        List<Match> matches = matchRepository.findByProjectCompanyId(companyId);
        matches.forEach(this::refreshMatchScore);
        return matches;
    }

    // Espelho de getPendingInvitesForProfessional: interesses que profissionais enviaram
    // pra vagas da empresa (professionalShowsInterest) e que ainda aguardam resposta dela.
    @Transactional
    public List<Match> getReceivedInterestsForCompany(Long companyId) {
        List<Match> received = matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED)
                .filter(this::isActionablePending)
                .toList();

        received.forEach(this::refreshMatchScore);

        return received;
    }

    // Espelho de getSentInterestsForProfessional: convites que a empresa enviou (a partir
    // do ranking) e que ainda aguardam resposta do profissional.
    @Transactional
    public List<Match> getSentInvitesForCompany(Long companyId) {
        List<Match> sent = matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.COMPANY_INTERESTED)
                .filter(this::isActionablePending)
                .toList();

        // Convite que o profissional já começou a responder sai daqui e vai pra
        // getInScreeningMatchesForCompany -- ver mesmo comentário em getPendingInvitesForProfessional.
        Set<Long> inScreening = activeScreeningMatchIds(sent);
        List<Match> result = sent.stream().filter(m -> !inScreening.contains(m.getId())).toList();

        result.forEach(this::refreshMatchScore);

        return result;
    }

    // "Em processo" (empresa): candidaturas ainda sem decisão final, mas que já têm um processo
    // seletivo em andamento por trás -- hoje ficariam invisíveis (interesse recém-demonstrado,
    // WAITING, nunca aparece em nenhuma aba) ou misturadas com convites que a empresa já pode
    // aceitar/recusar direto (COMPANY_INTERESTED sem etapa pendente). Espelho de
    // getInScreeningMatchesForProfessional.
    @Transactional
    public List<Match> getInScreeningMatchesForCompany(Long companyId) {
        List<Match> candidates = matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.WAITING
                          || m.getStatus() == StatusMatch.COMPANY_INTERESTED)
                .filter(this::isActionablePending)
                .toList();

        Set<Long> inScreening = activeScreeningMatchIds(candidates);
        List<Match> result = candidates.stream().filter(m -> inScreening.contains(m.getId())).toList();

        result.forEach(this::refreshMatchScore);

        return result;
    }

    // Só matches MATCHED e ATIVOS -- os já encerrados (active=false) saem daqui e
    // aparecem em getPreviousProjectsByCompany, não nos dois ao mesmo tempo.
    @Transactional
    public List<Match> getConfirmedMatchesForCompany(Long companyId) {
        // Filtro (MATCHED + ativo) agora resolvido no banco -- ver
        // findConfirmedActiveByCompanyId.
        List<Match> confirmed = matchRepository.findConfirmedActiveByCompanyId(companyId);

        confirmed.forEach(this::refreshMatchScore);

        return confirmed;
    }

    // Matches recusados (por qualquer um dos dois lados) — sem filtro de vaga aberta, já
    // que a recusa é definitiva independente do que aconteça com a vaga depois.
    public List<Match> getRejectedMatchesForCompany(Long companyId) {
        return matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.REJECTED)
                .toList();
    }

    public List<Match> getPreviousProjectsByCompany(Long companyId) {
        return matchRepository.findPreviousProjectsByCompanyId(companyId);
    }

    // Espelho de getPreviousProjectsByCompany, do lado do profissional.
    public List<Match> getPreviousProjectsByProfessional(Long professionalId) {
        return matchRepository.findPreviousProjectsByProfessionalId(professionalId);
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
    // Package-private (não private): ProposalService, no mesmo pacote, reaproveita a mesma
    // checagem ao aceitar uma proposta -- mesmo motivo de refreshMatchScore logo acima.
    void assertProjectIsOpen(Project project) {
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This opportunity is not open.");
        }
    }

    // Barra a confirmação de mais um match quando o projeto já não tem vagas livres (encerrado,
    // pausado por ter atingido o limite, ou outro match concorrente acabou de preenchê-lo).
    // Package-private pelo mesmo motivo de assertProjectIsOpen -- reaproveitado por ProposalService.
    void assertProjectHasOpenPositions(Project project) {
        assertProjectIsOpen(project);
        if (!project.hasOpenPositions()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This opportunity has reached its position limit.");
        }
    }

    // Efeito cascata de pausar projeto
    // Package-private pelo mesmo motivo de assertProjectIsOpen -- reaproveitado por ProposalService
    // ao aceitar uma proposta (também preenche uma posição).
    void incrementFilledPositions(Project project) {
        // UPDATE atomico no banco
        projectRepository.incrementFilledPositions(project.getId());
        // a query acima é um bulk update — sincroniza o objeto em memória pra que a checagem
        // de limite logo abaixo (e qualquer leitura subsequente na mesma transação) veja o valor atual
        project.setFilledPositions(project.getFilledPositions() + 1);
        // Verifica se esta cheio, e pausa se for o caso
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
        //Verifica se o projeto esta em aberto
        if (project.getStatus() != ProjectStatus.OPEN) {
            return;
        }
        // Verifica se realmente acabaram as posicoes disponiveis
        if (project.getFilledPositions() < project.getMaxPositions()) {
            return;
        }

        // Pausa o projeto
        project.setStatus(ProjectStatus.PAUSED);
        projectRepository.save(project);

        // Notifica e manda um email para empresa, falando que o porjeto foi pausado
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