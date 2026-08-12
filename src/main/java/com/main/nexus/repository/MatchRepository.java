package com.main.nexus.repository;

import com.main.nexus.model.Match;
import com.main.nexus.model.enums.StatusMatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByProjectId(Long projectId);
    List<Match> findByProfessionalId(Long professionalId);
    List<Match> findByStatus(StatusMatch status);
    @Query("SELECT m FROM Match m WHERE m.status = :status AND m.createdAt < :createdAtBefore " +
           "AND (m.active = true OR m.active IS NULL)")
    List<Match> findByStatusAndCreatedAtBeforeAndActiveTrue(
            @Param("status") StatusMatch status,
            @Param("createdAtBefore") java.time.LocalDateTime createdAtBefore);
    Optional<Match> findByProjectIdAndProfessionalId(Long projectId, Long professionalId);
    List<Match> findByProjectCompanyId(Long companyId);
    
    long countByStatus(StatusMatch status);
    long countByProfessionalId(Long professionalId);
    long countByProfessionalIdAndStatus(Long professionalId, StatusMatch status);

    @Query("SELECT m FROM Match m WHERE m.project.id = :projectId ORDER BY m.matchScore DESC")
    List<Match> findByProjectIdOrderByMatchScoreDesc(Long projectId);
    
    @Query("SELECT AVG(m.matchScore) FROM Match m")
    Double findAverageMatchScore();

    @Query("SELECT AVG(m.matchScore) FROM Match m WHERE m.professional.id = :professionalId")
    Double findAverageScoreByProfessionalId(Long professionalId);

    // Média de score dos matches de uma empresa
    @Query("SELECT AVG(m.matchScore) FROM Match m WHERE m.project.company.id = :companyId")
    Double findAverageMatchScoreByCompany(@Param("companyId") Long companyId);

    // Contagem por status de uma empresa
    @Query("SELECT COUNT(m) FROM Match m WHERE m.project.company.id = :companyId AND m.status = :status")
    long countByCompanyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") StatusMatch status);

    // Contagem total de matches de uma empresa
    @Query("SELECT COUNT(m) FROM Match m WHERE m.project.company.id = :companyId")
    long countByCompanyId(@Param("companyId") Long companyId);

    // Projetos anteriores de uma empresa: matches que foram confirmados (MATCHED em algum
    // momento) e já encerraram — seja porque expiraram naturalmente após 30 dias
    // (MatchExpirationService só marca active=false, mantendo status=MATCHED) ou porque
    // foram cancelados depois de confirmados (cancelMatchInternal também marca
    // active=false, mas muda status para REJECTED).
    @Query("SELECT DISTINCT m FROM Match m WHERE m.project.company.id = :companyId " +
           "AND m.active = false " +
           "AND (m.status = com.main.nexus.model.enums.StatusMatch.MATCHED " +
           "     OR EXISTS (SELECT 1 FROM MatchHistory h WHERE h.match = m AND h.toStatus = 'MATCHED')) " +
           "ORDER BY m.createdAt DESC")
    List<Match> findPreviousProjectsByCompanyId(@Param("companyId") Long companyId);

    // Matches por mês de uma empresa retorna [year, month, status, count]
    // WAITING fora: é só o candidato pontuado automaticamente pelo ranking, nunca virou
    // convite/interesse real (mesmo critério de buildMatchSummary), não deve inflar o total.
    @Query("SELECT YEAR(m.createdAt), MONTH(m.createdAt), m.status, COUNT(m) " +
           "FROM Match m " +
           "WHERE m.project.company.id = :companyId " +
           "AND m.createdAt >= :since " +
           "AND m.status != com.main.nexus.model.enums.StatusMatch.WAITING " +
           "GROUP BY YEAR(m.createdAt), MONTH(m.createdAt), m.status " +
           "ORDER BY YEAR(m.createdAt) ASC, MONTH(m.createdAt) ASC")
    List<Object[]> findMonthlyMatchStatsByCompany(
            @Param("companyId") Long companyId,
            @Param("since") java.time.LocalDateTime since);

    // Scores de todos os matches de uma empresa para calcular distribuição
    @Query("SELECT m.matchScore FROM Match m WHERE m.project.company.id = :companyId")
    List<Double> findAllScoresByCompany(@Param("companyId") Long companyId);

    // Matches de um projeto específico com score e status
    @Query("SELECT m FROM Match m WHERE m.project.id = :projectId ORDER BY m.matchScore DESC")
    List<Match> findByProjectIdOrderByScore(@Param("projectId") Long projectId);

    // Matches por mês de um profissional retorna [year, month, status, count]
    // WAITING fora: é só o candidato pontuado automaticamente pelo ranking, nunca virou
    // convite/interesse real (mesmo critério de buildMatchSummary), não deve inflar o total.
    @Query("SELECT YEAR(m.createdAt), MONTH(m.createdAt), m.status, COUNT(m) " +
           "FROM Match m " +
           "WHERE m.professional.id = :professionalId " +
           "AND m.createdAt >= :since " +
           "AND m.status != com.main.nexus.model.enums.StatusMatch.WAITING " +
           "GROUP BY YEAR(m.createdAt), MONTH(m.createdAt), m.status " +
           "ORDER BY YEAR(m.createdAt) ASC, MONTH(m.createdAt) ASC")
    List<Object[]> findMonthlyMatchStatsByProfessional(
            @Param("professionalId") Long professionalId,
            @Param("since") java.time.LocalDateTime since);

    // Scores de todos os matches de um profissional para calcular distribuição
    @Query("SELECT m.matchScore FROM Match m WHERE m.professional.id = :professionalId")
    List<Double> findAllScoresByProfessional(@Param("professionalId") Long professionalId);

    // Skills mais presentes nos projetos em que o profissional deu match
    @Query("SELECT s.name, s.category, COUNT(DISTINCT m.project) " +
           "FROM Match m JOIN m.project.requiredSkills s " +
           "WHERE m.professional.id = :professionalId " +
           "GROUP BY s.name, s.category " +
           "ORDER BY COUNT(DISTINCT m.project) DESC")
    List<Object[]> findMostRequiredSkillsByProfessional(@Param("professionalId") Long professionalId);

    // Matches confirmados há tempo suficiente cujo status check ainda não foi respondido
    // pela empresa — usado tanto pelo lembrete automático quanto pra exibir a pergunta
    // direto no dashboard, sem a empresa precisar abrir a notificação.
    @Query("SELECT m FROM Match m WHERE m.project.company.id = :companyId " +
           "AND m.status = com.main.nexus.model.enums.StatusMatch.MATCHED " +
           "AND (m.active = true OR m.active IS NULL) " +
           "AND m.createdAt < :threshold " +
           "AND NOT EXISTS (SELECT 1 FROM MatchStatusCheck c WHERE c.match = m) " +
           "ORDER BY m.createdAt ASC")
    List<Match> findPendingStatusChecksByCompanyId(
            @Param("companyId") Long companyId,
            @Param("threshold") java.time.LocalDateTime threshold);

    // Matches que expiraram (30 dias, active=false) e ainda não têm avaliação desse lado —
    // usado pra abrir a avaliação automaticamente no dashboard, igual ao status check.
    @Query("SELECT m FROM Match m WHERE m.professional.id = :professionalId " +
           "AND m.status = com.main.nexus.model.enums.StatusMatch.MATCHED " +
           "AND m.active = false " +
           "AND NOT EXISTS (SELECT 1 FROM Review r WHERE r.match = m " +
           "                AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL) " +
           "ORDER BY m.createdAt ASC")
    List<Match> findPendingReviewsForProfessional(@Param("professionalId") Long professionalId);

    @Query("SELECT m FROM Match m WHERE m.project.company.id = :companyId " +
           "AND m.status = com.main.nexus.model.enums.StatusMatch.MATCHED " +
           "AND m.active = false " +
           "AND NOT EXISTS (SELECT 1 FROM Review r WHERE r.match = m " +
           "                AND r.authorType = com.main.nexus.model.enums.AuthorType.COMPANY) " +
           "ORDER BY m.createdAt ASC")
    List<Match> findPendingReviewsForCompany(@Param("companyId") Long companyId);
}