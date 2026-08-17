package com.main.nexus.repository;

import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.enums.AuthorType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RejectionFeedbackRepository extends JpaRepository<RejectionFeedback, Long> {

    // Usado para exibir motivos + observação no card de match recusado
    Optional<RejectionFeedback> findByMatchId(Long matchId);

    @Query("SELECT r FROM RejectionFeedback r WHERE r.match.professional.id = :professionalId " +
           "AND r.rejectedBy = :rejectedBy")
    List<RejectionFeedback> findByMatchProfessionalAndRejectedBy(
            @Param("professionalId") Long professionalId,
            @Param("rejectedBy") AuthorType rejectedBy);

    @Query("SELECT COUNT(r) FROM RejectionFeedback r WHERE r.match.professional.id = :professionalId " +
           "AND r.rejectedBy = :rejectedBy")
    long countByMatchProfessionalIdAndRejectedBy(
            @Param("professionalId") Long professionalId,
            @Param("rejectedBy") AuthorType rejectedBy);

    @Query("SELECT r FROM RejectionFeedback r WHERE r.match.project.company.id = :companyId " +
           "AND r.rejectedBy = :rejectedBy")
    List<RejectionFeedback> findByMatchProjectCompanyAndRejectedBy(
            @Param("companyId") Long companyId,
            @Param("rejectedBy") AuthorType rejectedBy);
}
