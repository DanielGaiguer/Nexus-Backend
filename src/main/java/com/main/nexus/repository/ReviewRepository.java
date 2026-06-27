package com.main.nexus.repository;

import com.main.nexus.model.Review;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMatchId(Long matchId);

    boolean existsByMatchIdAndAuthorType(Long matchId, com.main.nexus.model.enums.AuthorType authorType);

    // Média de notas recebidas por um profissional (todas, sem filtro de data)
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.professional.id = :professionalId")
    Double findAverageRatingByProfessionalId(@Param("professionalId") Long professionalId);

    // Média de notas recebidas por uma empresa (todas, sem filtro de data)
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.project.company.id = :companyId")
    Double findAverageRatingByCompanyId(@Param("companyId") Long companyId);

    // Reviews que a EMPRESA fez sobre o PROFISSIONAL, dentro de um período
    @Query("SELECT r FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = 'COMPANY' AND r.createdAt >= :since")
    List<Review> findCompanyReviewsOfProfessional(
            @Param("professionalId") Long professionalId,
            @Param("since") LocalDateTime since);

    // Reviews que o PROFISSIONAL fez sobre a EMPRESA, dentro de um período
    @Query("SELECT r FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = 'PROFESSIONAL' AND r.createdAt >= :since")
    List<Review> findProfessionalReviewsOfCompany(
            @Param("companyId") Long companyId,
            @Param("since") LocalDateTime since);
}