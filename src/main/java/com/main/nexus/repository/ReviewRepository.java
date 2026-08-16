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

    // Avaliações recebidas pelo profissional (escritas pela empresa) — usado no card
    // SoftSkills do dashboard do profissional.
    @Query("SELECT r FROM Review r WHERE r.match.professional.id = :professionalId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.COMPANY")
    List<Review> findCompanyReviewsForProfessional(@Param("professionalId") Long professionalId);

    // Avaliações recebidas pela empresa (escritas pelo profissional) — usado no card
    // SoftSkills do dashboard da empresa.
    @Query("SELECT r FROM Review r WHERE r.match.project.company.id = :companyId " +
           "AND r.authorType = com.main.nexus.model.enums.AuthorType.PROFESSIONAL")
    List<Review> findProfessionalReviewsForCompany(@Param("companyId") Long companyId);
}
