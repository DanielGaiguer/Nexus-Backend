package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.InitiatedBy;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.RejectionReason;
import com.main.nexus.model.enums.StatusMatch;
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
    private EmailService emailService;

    // -------------------------------------------------------
    // SCORE ENGINE — fórmula principal
    // ScoreMatch = (Skills*0.35) + (Budget*0.25) + (History*0.20) + (Reputation*0.10) + (Availability*0.10)
    // -------------------------------------------------------
    public double calculateScore(Professional professional, Project project) {

        double skillScore       = calculateSkillScore(professional, project);
        double budgetScore      = calculateBudgetScore(professional, project);
        double historyScore     = calculateHistoryScore(professional);
        double reputationScore  = calculateReputationScore(professional);
        double availabilityScore = calculateAvailabilityScore(professional);

        return (skillScore       * 0.35)
             + (budgetScore      * 0.25)
             + (historyScore     * 0.20)
             + (reputationScore  * 0.10)
             + (availabilityScore * 0.10);
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

        // Usa a média da faixa do profissional como referência, em vez de só o mínimo
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
        int count = professional.getProjects()!= null
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
    
    // Gera ranking de profissionais para um projeto
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
            // Só recalcula matches que ainda não avançaram no fluxo de interesse
            // Não sobrescreve matches já confirmados ou rejeitados
            if (match.getStatus() == StatusMatch.WAITING) {
                double newScore = calculateScore(match.getProfessional(), project);
                match.setMatchScore(newScore);
            }
        }
        matchRepository.saveAll(existingMatches);

        // Gera matches novos para profissionais que ainda não tinham (ex: cadastrados depois)
        generateRankingForProject(project);
    }

    private boolean matchesProjectType(Professional professional, Project project) {
        return professional.getPreferredTypes().contains(project.getType());
    }

    // Fluxo bilateral de interesse

    // empresa demonstra interesse
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

    public Match companyRejectsWithFeedback(Long matchId, Long companyId, String reason) {
        Match match = findById(matchId);
        validateCompanyOwnership(match, companyId);

        match.setCompanyStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        Match saved = matchRepository.save(match);

        saveRejectionFeedback(match, reason);
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

    public Match professionalRejectsWithFeedback(Long matchId, Long professionalId, String reason) {
        Match match = findById(matchId);
        validateProfessionalOwnership(match, professionalId);

        match.setProfessionalStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        Match saved = matchRepository.save(match);

        saveRejectionFeedback(match, reason);
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

    // ── Helper de feedback reaproveitado pelos dois lados ──────────────────────

    private void saveRejectionFeedback(Match match, String reason) {
        RejectionReason rejectionReason;
        try {
            rejectionReason = RejectionReason.valueOf(reason.toUpperCase());
        } catch (IllegalArgumentException e) {
            rejectionReason = RejectionReason.OTHER;
        }

        RejectionFeedback feedback = new RejectionFeedback();
        feedback.setProfessional(match.getProfessional());
        feedback.setProject(match.getProject());
        feedback.setReason(rejectionReason);
        rejectionFeedbackRepository.save(feedback);
    }

    // profissional demonstra interesse
    // ── Profissional vê oportunidades compatíveis ──────────────────────────────

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

    // ── Profissional demonstra interesse num projeto ───────────────────────────
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

        // Verifica o estado ANTES de qualquer alteração
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

    // Consultas

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
    
    // Busca um match pelo id
    public Match findById(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + id));
    }

    // Convites pendentes para o profissional (empresa já demonstrou interesse)
    public List<Match> getPendingInvitesForProfessional(Long professionalId) {
        return matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.COMPANY_INTERESTED)
                .toList();
    }

    // Matches confirmados para o profissional
    public List<Match> getConfirmedMatchesForProfessional(Long professionalId) {
        return matchRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .toList();
    }

    // Matches confirmados de uma empresa
    public long countConfirmedMatchesByCompany(Long companyId) {
        return matchRepository.findByProjectCompanyId(companyId)
                .stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .count();
    }
    
    private void notifyMutualMatch(Match match) {
        String professionalEmail = match.getProfessional().getUser().getEmail();
        String companyEmail = match.getProject().getCompany().getUser().getEmail();
        String professionalName = match.getProfessional().getName();
        String companyName = match.getProject().getCompany().getCompanyName();
        String projectTitle = match.getProject().getTitle();

        if (match.getInitiatedBy() == InitiatedBy.PROFESSIONAL) {
            // Profissional iniciou, empresa correspondeu agora — avisa o profissional
            emailService.send(
                professionalEmail,
                "Seu interesse foi correspondido! — Nexus",
                "Olá " + professionalName + ",\n\n" +
                companyName + " também demonstrou interesse em você para o projeto \"" + projectTitle + "\".\n\n" +
                "O match foi confirmado e os contatos já estão disponíveis no Nexus.\n\nEquipe Nexus"
            );
        } else if (match.getInitiatedBy() == InitiatedBy.COMPANY) {
            // Empresa iniciou, profissional correspondeu agora — avisa a empresa
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