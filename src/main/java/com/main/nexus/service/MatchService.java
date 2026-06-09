package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.RejectionReason;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.RejectionFeedbackRepository;
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
        Double projMax = project.getMaximumBudget();
        Double projMin = project.getMinimumBudget();

        if (profMin == null || projMax == null) return 50.0; // sem info = neutro

        if (profMin <= projMax) {
            // Dentro do orçamento — quanto mais próximo do mínimo, melhor
            if (projMin != null && projMin > 0) {
                double ratio = profMin / projMax;
                return Math.max(0, 100.0 - (ratio * 20)); // penaliza levemente quem pede mais
            }
            return 100.0;
        }

        // Acima do orçamento — penalidade progressiva
        double excesso = (profMin - projMax) / projMax;
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

    // -------------------------------------------------------
    // Gera ranking de profissionais para um projeto
    // -------------------------------------------------------
    public List<Match> generateRankingForProject(Project project) {
        List<Professional> allProfessionals = professionalRepository.findAll();
        List<Match> matches = new ArrayList<>();

        for (Professional professional : allProfessionals) {
            // Verifica se já existe um match para esse par
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
        }

        // Ordena por score decrescente e salva
        matches.sort(Comparator.comparingDouble(Match::getMatchScore).reversed());
        return matchRepository.saveAll(matches);
    }

    // -------------------------------------------------------
    // Fluxo bilateral de interesse
    // -------------------------------------------------------

    // Empresa demonstra interesse em um profissional
    public Match companyShowsInterest(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        match.setCompanyStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.COMPANY_INTERESTED);

        return matchRepository.save(match);
    }

    // Profissional aceita o convite
    public Match professionalAccepts(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        match.setProfessionalStatus(InterestStatus.INTERESTED);
        match.setStatus(StatusMatch.MATCHED); // contatos liberados

        return matchRepository.save(match);
    }

    // Profissional recusa o convite
    public Match professionalRejects(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        match.setProfessionalStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);

        return matchRepository.save(match);
    }

    // -------------------------------------------------------
    // Consultas
    // -------------------------------------------------------

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

    // Profissional rejeita e registra o motivo
    public Match professionalRejectsWithFeedback(Long matchId, String reason) {
        Match match = findById(matchId);
        match.setProfessionalStatus(InterestStatus.REJECTED);
        match.setStatus(StatusMatch.REJECTED);
        Match saved = matchRepository.save(match);

        // Registra o feedback para o aprendizado contínuo
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

        return saved;
    }
}