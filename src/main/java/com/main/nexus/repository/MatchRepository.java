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

    // Matches por mês de uma empresa — retorna [year, month, status, count]
    @Query("SELECT YEAR(m.createdAt), MONTH(m.createdAt), m.status, COUNT(m) " +
           "FROM Match m " +
           "WHERE m.project.company.id = :companyId " +
           "AND m.createdAt >= :since " +
           "GROUP BY YEAR(m.createdAt), MONTH(m.createdAt), m.status " +
           "ORDER BY YEAR(m.createdAt) ASC, MONTH(m.createdAt) ASC")
    List<Object[]> findMonthlyMatchStatsByCompany(
            @Param("companyId") Long companyId,
            @Param("since") java.time.LocalDateTime since);

    // Scores de todos os matches de uma empresa — para calcular distribuição
    @Query("SELECT m.matchScore FROM Match m WHERE m.project.company.id = :companyId")
    List<Double> findAllScoresByCompany(@Param("companyId") Long companyId);

    // Matches de um projeto específico com score e status
    @Query("SELECT m FROM Match m WHERE m.project.id = :projectId ORDER BY m.matchScore DESC")
    List<Match> findByProjectIdOrderByScore(@Param("projectId") Long projectId);
}