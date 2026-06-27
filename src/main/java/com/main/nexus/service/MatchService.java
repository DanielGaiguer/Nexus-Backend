package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.Review;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CompanyRejectionReason;
import com.main.nexus.model.enums.InitiatedBy;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.ProfessionalRejectionReason;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.RejectionFeedbackRepository;
import com.main.nexus.repository.ReviewRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private ReviewRepository reviewRepository;

    @Autowired
    private EmailService emailService;

    // ── Constantes de penalidade comportamental ──────────────────────────────
    private static final int MIN_OCCURRENCES_TO_COUNT = 3;
    private static final double MAX_TOTAL_PENALTY = 0.25;

    // -------------------------------------------------------
    // SCORE ENGINE — fórmula principal
    // ScoreMatch = (Skills*0.35) + (Budget*0.25) + (History*0.20) + (Reputation*0.10) + (Availability*0.10)
    // ou, para ONSITE/HYBRID:
    // ScoreMatch = (Skills*0.30) + (Budget*0.20) + (History*0.17) + (Reputation*0.09) + (Availability*0.09) + (Distance*0.15)
    // -------------------------------------------------------
    public double calculateScore(Professional professional, Project project) {

        double skillScore        = calculateSkillScore(professional, project);
        double budgetScore       = calculateBudgetScore(professional, project);
        double historyScore      = calculateHistoryScore(professional);
        double reputationScore   = calculateReputationScore(professional);
        double availabilityScore = calculateAvailabilityScore(professional);

        boolean considersDistance = project.getWorkMode() == Modality.ONSITE
                                  || project.getWorkMode() == Modality.HYBRID;

        double baseScore;

        if (considersDistance) {
            double distanceScore = calculateDistanceScore(professional, project);

            baseScore = (skillScore        * 0.30)
                      + (budgetScore       * 0.20)
                      + (historyScore      * 0.17)
                      + (reputationScore   * 0.09)
                      + (availabilityScore * 0.09)
                      + (distanceScore     * 0.15);
        } else {
            baseScore = (skillScore        * 0.35)
                      + (budgetScore       * 0.25)
                      + (historyScore      * 0.20)
                      + (reputationScore   * 0.10)
                      + (availabilityScore * 0.10);
        }

        // ── Camada de penalidade comportamental (pós-cálculo, fora da fórmula formal) ──
        double professionalPenalty = calculateProfessionalBehaviorPenalty(professional);
        double companyPenalty = calculateCompanyBehaviorPenalty(project.getCompany());

        double totalPenalty = Math.min(professionalPenalty + companyPenalty, MAX_TOTAL_PENALTY);
        double finalMultiplier = 1.0 - totalPenalty;

        return baseScore * finalMultiplier;
    }

    // Skills: quantas skills da vaga o profissional possui / total exigido * 100
    private double calculateSkillScore(Professional professional, Project project) {
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

    // Orçamento: pretensão do profissional está dentro do range da vaga?
    private double calculateBudgetScore(Professional professional, Project project) {
        Double profMin = professional.getMinimumSalaryExpectation();
        Double profMax = professional.getMaximumSalaryExpectation();
        Double projMax = project.getMaximumBudget();
        Double projMin = project.getMinimumBudget();

        if (profMin == null || projMax == null) return 50.0;

        double profExpectation = (profMax != null) ? (profMin + profMax) / 2.0 : profMin;

        if (profExpectation <= projMax) {
            if (projMin != null && projMin > 0) {
                double ratio = profExpectation / projMax;
                return Math.max(0, 100.0 - (ratio * 20));
            }
            return 100.0;
        }

        double excesso = (profExpectation - projMax) / projMax;
        return Math.max(0, 100.0 - (excesso * 100));
    }

    // Histórico: baseado na quantidade de projetos anteriores (máx 10 projetos = 100)
    private double calculateHistoryScore(Professional professional) {
        int count = professional.getProjects() != null
                ? professional.getProjects().size()
                : 0;
        return Math.min(count * 10.0, 100.0);
    }

    // Reputação: média de estrelas (1-5) normalizada para 0-100
    private double calculateReputationScore(Professional professional) {
        double rep = professional.getReputation() != null ? professional.getReputation() : 0.0;
        return (rep / 5.0) * 100.0;
    }

    // Disponibilidade: disponível = 100, indisponível = 0
    private double calculateAvailabilityScore(Professional professional) {
        return Boolean.TRUE.equals(professional.getAvailable()) ? 100.0 : 0.0;
    }

    // Distância: calculada via Haversine entre profissional e empresa
    private double calculateDistanceScore(Professional professional, Project project) {
        Double profLat = professional.getLatitude();
        Double profLon = professional.getLongitude();
        Double companyLat = project.getCompany().getLatitude();
        Double companyLon = project.getCompany().getLongitude();

        if (profLat == null || profLon == null || companyLat == null || companyLon == null) {
            return 50.0;
        }

        double distanceKm = haversineDistance(profLat, profLon, companyLat, companyLon);
        return scoreFromDistance(distanceKm);
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
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

    private double scoreFromDistance(double distanceKm) {
        if (distanceKm <= 5)   return 100.0;
        if (distanceKm <= 10)  return 90.0;
        if (distanceKm <= 20)  return 75.0;
        if (distanceKm <= 50)  return 50.0;
        if (distanceKm <= 100) return 25.0;
        return 10.0;
    }

    // =========================================================
    // PENALIDADE COMPORTAMENTAL — camada pós-cálculo
    // =========================================================

    // ── Penalidade do PROFISSIONAL (baseada em como o mercado reage a ele) ──

    private double calculateProfessionalBehaviorPenalty(Professional professional) {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);

        List<RejectionFeedback> rejections = rejectionFeedbackRepository
                .findCompanyRejectionsAgainstProfessional(professional.getId(), sixMonthsAgo);

        double volumePenalty = calculateVolumePenalty(rejections.size());
        double repetitionPenalty = calculateProfessionalRepetitionPenalty(rejections);

        List<Review> reviews = reviewRepository
                .findCompanyReviewsOfProfessional(professional.getId(), sixMonthsAgo);
        double reviewPenalty = calculateReviewPenalty(reviews);

        double totalPenalty = volumePenalty + repetitionPenalty + reviewPenalty;
        return Math.min(totalPenalty, MAX_TOTAL_PENALTY);
    }

    // ── Penalidade da EMPRESA (baseada em como profissionais reagem a ela) ──

    private double calculateCompanyBehaviorPenalty(Company company) {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);

        List<RejectionFeedback> rejections = rejectionFeedbackRepository
                .findProfessionalRejectionsAgainstCompany(company.getId(), sixMonthsAgo);

        double volumePenalty = calculateVolumePenalty(rejections.size());
        double repetitionPenalty = calculateCompanyRepetitionPenalty(rejections);

        List<Review> reviews = reviewRepository
                .findProfessionalReviewsOfCompany(company.getId(), sixMonthsAgo);
        double reviewPenalty = calculateReviewPenalty(reviews);

        double totalPenalty = volumePenalty + repetitionPenalty + reviewPenalty;
        return Math.min(totalPenalty, MAX_TOTAL_PENALTY);
    }

    // ── Componente A — volume geral de rejeições recebidas ──

    private double calculateVolumePenalty(int totalRejections) {
        if (totalRejections < MIN_OCCURRENCES_TO_COUNT) return 0.0;
        if (totalRejections <= 5)  return 0.03;
        if (totalRejections <= 10) return 0.06;
        return 0.10;
    }

    // ── Componente B — repetição do mesmo motivo (profissional sendo rejeitado pela empresa) ──

    private double calculateProfessionalRepetitionPenalty(List<RejectionFeedback> rejections) {
        Map<CompanyRejectionReason, Long> frequency = rejections.stream()
                .filter(r -> r.getCompanyReasons() != null)
                .flatMap(r -> r.getCompanyReasons().stream())
                .collect(Collectors.groupingBy(reason -> reason, Collectors.counting()));

        long maxFrequency = frequency.values().stream().max(Long::compare).orElse(0L);
        return repetitionPenaltyFromFrequency(maxFrequency);
    }

    // ── Componente B — repetição do mesmo motivo (empresa sendo rejeitada pelo profissional) ──

    private double calculateCompanyRepetitionPenalty(List<RejectionFeedback> rejections) {
        Map<ProfessionalRejectionReason, Long> frequency = rejections.stream()
                .filter(r -> r.getProfessionalReasons() != null)
                .flatMap(r -> r.getProfessionalReasons().stream())
                .collect(Collectors.groupingBy(reason -> reason, Collectors.counting()));

        long maxFrequency = frequency.values().stream().max(Long::compare).orElse(0L);
        return repetitionPenaltyFromFrequency(maxFrequency);
    }

    private double repetitionPenaltyFromFrequency(long maxFrequency) {
        if (maxFrequency < MIN_OCCURRENCES_TO_COUNT) return 0.0;
        if (maxFrequency <= 4) return 0.05;
        if (maxFrequency <= 7) return 0.10;
        return 0.15;
    }

    // ── Componente C — proporção de reviews negativos (notas 1-2) ──

    private double calculateReviewPenalty(List<Review> reviews) {
        if (reviews.size() < MIN_OCCURRENCES_TO_COUNT) return 0.0;

        long negativeCount = reviews.stream()
                .filter(r -> r.getRating() <= 2)
                .count();

        double negativeRatio = (double) negativeCount / reviews.size();

        if (negativeRatio >= 0.60) return 0.15;
        if (negativeRatio >= 0.40) return 0.08;
        if (negativeRatio >= 0.25) return 0.03;
        return 0.0;
    }

    // =========================================================
    // RANKING — geração e recálculo
    // =========================================================

    public List<Match> generateRankingForProject(Project project) {
        List<Professional> allProfessionals = professionalRepository.findAll();
        List<Match> matches = new ArrayList<>();

        for (Professional professional : allProfessionals) {
            if (!matchesProjectType(professional, project)) continue;

            boolean alreadyExists = matchRepository
                    .findByProjectIdAndProfessionalId(project.getId(), professional.getId())
                    .isPresent();
            if (alreadyExists) continue;

            double score = calculateScore(professional, project);

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
                double newScore = calculateScore(match.getProfessional(), project);
                match.setMatchScore(newScore);
            }
        }
        matchRepository.saveAll(existingMatches);

        generateRankingForProject(project);
    }

    private boolean matchesProjectType(Professional professional, Project project) {
        return professional.getPreferredTypes().contains(project.getType());
    }

    // =========================================================
    // FLUXO BILATERAL DE INTERESSE
    // =========================================================

    public Match companyShowsInterest(Long matchId, Long companyId) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        boolean wasAlreadyProfessionalInterested = match.getStatus() == StatusMatch.PROFESSIONAL_INTERESTED;

        if (wasAlreadyProfessionalInterested) {
            match.setCompanyStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.MATCHED);
        } else {
            match.setCompanyStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.COMPANY_INTERESTED);
            match.setInitiatedBy(InitiatedBy.COMPANY);
        }

        Match saved = matchRepository.save(match);

        if (wasAlreadyProfessionalInterested) {
            notifyMutualMatch(saved);
        } else {
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

    public Match companyAccepts(Long matchId, Long companyId) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        if (match.getStatus() != StatusMatch.PROFESSIONAL_INTERESTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not awaiting a company response.");
        }

        match.setCompanyStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.MATCHED);
        return matchRepository.save(match);
    }

    public Match companyRejectsWithFeedback(Long matchId, Long companyId, List<String> reasons) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        match.setCompanyStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        Match saved = matchRepository.save(match);

        saveCompanyRejection(match, reasons);
        return saved;
    }

    public Match professionalAccepts(Long matchId, Long professionalId) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);

        if (match.getStatus() != StatusMatch.COMPANY_INTERESTED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not awaiting a professional response.");
        }

        match.setProfessionalStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.MATCHED);
        return matchRepository.save(match);
    }

    public Match professionalRejectsWithFeedback(Long matchId, Long professionalId, List<String> reasons) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);

        match.setProfessionalStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        Match saved = matchRepository.save(match);

        saveProfessionalRejection(match, reasons);
        return saved;
    }

    // ── Validações de posse ───────────────────────────────────────────────────

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

    // ── Persistência de feedback de rejeição, separado por quem rejeita ────────

    private void saveProfessionalRejection(Match match, List<String> reasonStrings) {
        List<ProfessionalRejectionReason> reasons = reasonStrings.stream()
                .map(r -> {
                    try {
                        return ProfessionalRejectionReason.valueOf(r.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return ProfessionalRejectionReason.OTHER;
                    }
                })
                .toList();

        RejectionFeedback feedback = new RejectionFeedback();
        feedback.setProfessional(match.getProfessional());
        feedback.setProject(match.getProject());
        feedback.setRejectedBy(AuthorType.PROFESSIONAL);
        feedback.setProfessionalReasons(reasons);
        rejectionFeedbackRepository.save(feedback);
    }

    private void saveCompanyRejection(Match match, List<String> reasonStrings) {
        List<CompanyRejectionReason> reasons = reasonStrings.stream()
                .map(r -> {
                    try {
                        return CompanyRejectionReason.valueOf(r.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return CompanyRejectionReason.OTHER;
                    }
                })
                .toList();

        RejectionFeedback feedback = new RejectionFeedback();
        feedback.setProfessional(match.getProfessional());
        feedback.setProject(match.getProject());
        feedback.setRejectedBy(AuthorType.COMPANY);
        feedback.setCompanyReasons(reasons);
        rejectionFeedbackRepository.save(feedback);
    }

    // =========================================================
    // PROFISSIONAL — OPORTUNIDADES E INICIATIVA
    // =========================================================

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
                match.setMatchScore(calculateScore(professional, project));
                matchRepository.save(match);
            }
        }

        return matchRepository.findByProfessionalId(professionalId)
                .stream()
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
                    newMatch.setMatchScore(calculateScore(professional, project));
                    newMatch.setInitiatedBy(InitiatedBy.PROFESSIONAL);
                    return newMatch;
                });

        boolean wasAlreadyCompanyInterested = match.getStatus() == StatusMatch.COMPANY_INTERESTED;

        if (wasAlreadyCompanyInterested) {
            match.setProfessionalStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.MATCHED);
        } else {
            match.setProfessionalStatus(InterestStatus.INTERESTED);
            match.setStatus(StatusMatch.PROFESSIONAL_INTERESTED);
        }

        Match saved = matchRepository.save(match);

        if (wasAlreadyCompanyInterested) {
            notifyMutualMatch(saved);
        }

        return saved;
    }

    // =========================================================
    // CONSULTAS
    // =========================================================

    public List<Match> getRankingByProject(Long projectId) {
        return matchRepository.findByProjectId(projectId)
                .stream()
                .sorted(Comparator.comparingDouble(Match::getMatchScore).reversed())
                .toList();
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

    public long countConfirmedMatchesByCompany(Long companyId) {
        return matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .count();
    }

    // =========================================================
    // NOTIFICAÇÕES
    // =========================================================

    private void notifyMutualMatch(Match match) {
        String professionalEmail = match.getProfessional().getUser().getEmail();
        String companyEmail = match.getProject().getCompany().getUser().getEmail();
        String professionalName = match.getProfessional().getName();
        String companyName = match.getProject().getCompany().getCompanyName();
        String projectTitle = match.getProject().getTitle();

        if (match.getInitiatedBy() == InitiatedBy.PROFESSIONAL) {
            emailService.send(
                professionalEmail,
                "Seu interesse foi correspondido! — Nexus",
                "Olá " + professionalName + ",\n\n" +
                companyName + " também demonstrou interesse em você para o projeto \"" + projectTitle + "\".\n\n" +
                "O match foi confirmado e os contatos já estão disponíveis no Nexus.\n\nEquipe Nexus"
            );
        } else if (match.getInitiatedBy() == InitiatedBy.COMPANY) {
            emailService.send(
                companyEmail,
                "Seu interesse foi correspondido! — Nexus",
                "Olá " + companyName + ",\n\n" +
                professionalName + " também demonstrou interesse no projeto \"" + projectTitle + "\".\n\n" +
                "O match foi confirmado e os contatos já estão disponíveis no Nexus.\n\nEquipe Nexus"
            );
        }
    }
}