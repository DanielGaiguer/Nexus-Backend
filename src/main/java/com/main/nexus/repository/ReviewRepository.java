package com.main.nexus.repository;

import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMatchId(Long matchId);

    boolean existsByMatchIdAndAuthorType(Long matchId, AuthorType authorType);

    List<Review> findByMatchProfessionalId(Long professionalId);

    List<Review> findByMatchProjectCompanyId(Long companyId);

    // IDs dos matches que esse profissional/empresa já avaliou (como autor) — usado pra
    // trocar o botão "Avaliar" por "Avaliado" na listagem de matches.
    @Query("SELECT r.match.id FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL")
    List<Long> findReviewedMatchIdsByProfessional(@Param("professionalId") Long professionalId);

    @Query("SELECT r.match.id FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.COMPANY")
    List<Long> findReviewedMatchIdsByCompany(@Param("companyId") Long companyId);

    // Avaliações recebidas pelo profissional (escritas pela empresa), maior nota e mais
    // recente primeiro — usado no card SoftSkills e na exibição de avaliações do perfil.
    @Query("SELECT r FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.COMPANY " +
           "ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findCompanyReviewsForProfessional(@Param("professionalId") Long professionalId);

    // Avaliações recebidas pela empresa (escritas pelo profissional), maior nota e mais
    // recente primeiro — usado no card SoftSkills e na exibição de avaliações do perfil.
    @Query("SELECT r FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL " +
           "ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findProfessionalReviewsForCompany(@Param("companyId") Long companyId);

    // As melhores avaliações do profissional — usado no card de preview do perfil.
    // Chamar com PageRequest.of(0, size).
    @Query("SELECT r FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.COMPANY " +
           "ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findTop3ByProfessionalId(
            @Param("professionalId") Long professionalId, org.springframework.data.domain.Pageable pageable);

    // As melhores avaliações da empresa — usado no card de preview do perfil.
    // Chamar com PageRequest.of(0, size).
    @Query("SELECT r FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL " +
           "ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findTop3ByCompanyId(
            @Param("companyId") Long companyId, org.springframework.data.domain.Pageable pageable);

    // Avaliações do profissional filtradas por nota — usado na página dedicada de
    // avaliações com o filtro de estrelas.
    @Query("SELECT r FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.COMPANY " +
           "AND (:rating IS NULL OR r.rating = :rating) " +
           "ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findByProfessionalIdAndRating(
            @Param("professionalId") Long professionalId, @Param("rating") Integer rating);

    // Avaliações da empresa filtradas por nota — usado na página dedicada de
    // avaliações com o filtro de estrelas.
    @Query("SELECT r FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL " +
           "AND (:rating IS NULL OR r.rating = :rating) " +
           "ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findByCompanyIdAndRating(
            @Param("companyId") Long companyId, @Param("rating") Integer rating);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.COMPANY")
    Double findAverageRatingByProfessionalId(@Param("professionalId") Long professionalId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL")
    Double findAverageRatingByCompanyId(@Param("companyId") Long companyId);

    long countByMatchProfessionalIdAndAuthorType(Long professionalId, AuthorType authorType);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL")
    long countByCompanyId(@Param("companyId") Long companyId);
}
