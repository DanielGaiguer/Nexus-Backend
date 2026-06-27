package com.main.nexus.repository;

import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMatchId(Long matchId);

    boolean existsByMatchIdAndAuthorType(Long matchId, AuthorType authorType);

    // Todas as reviews recebidas por um profissional (histórico completo, sem filtro de data)
    List<Review> findByMatchProfessionalId(Long professionalId);

    // Todas as reviews recebidas por uma empresa (histórico completo, sem filtro de data)
    List<Review> findByMatchProjectCompanyId(Long companyId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.professional.id = :professionalId")
    Double findAverageRatingByProfessionalId(@Param("professionalId") Long professionalId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.project.company.id = :companyId")
    Double findAverageRatingByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT r FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = 'COMPANY' AND r.createdAt >= :since")
    List<Review> findCompanyReviewsOfProfessional(
            @Param("professionalId") Long professionalId,
            @Param("since") LocalDateTime since);

    @Query("SELECT r FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = 'PROFESSIONAL' AND r.createdAt >= :since")
    List<Review> findProfessionalReviewsOfCompany(
            @Param("companyId") Long companyId,
            @Param("since") LocalDateTime since);
}