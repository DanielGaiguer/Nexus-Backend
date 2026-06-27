package com.main.nexus.repository;

import com.main.nexus.model.RejectionFeedback;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RejectionFeedbackRepository extends JpaRepository<RejectionFeedback, Long> {

    @Query("SELECT r FROM RejectionFeedback r WHERE r.professional.id = :professionalId " +
           "AND r.rejectedBy = 'COMPANY' AND r.createdAt >= :since")
    List<RejectionFeedback> findCompanyRejectionsAgainstProfessional(
            @Param("professionalId") Long professionalId,
            @Param("since") LocalDateTime since);

    @Query("SELECT r FROM RejectionFeedback r WHERE r.project.company.id = :companyId " +
           "AND r.rejectedBy = 'PROFESSIONAL' AND r.createdAt >= :since")
    List<RejectionFeedback> findProfessionalRejectionsAgainstCompany(
            @Param("companyId") Long companyId,
            @Param("since") LocalDateTime since);
}