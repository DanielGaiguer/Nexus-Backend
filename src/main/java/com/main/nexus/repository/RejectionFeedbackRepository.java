// RejectionFeedbackRepository
package com.main.nexus.repository;

import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.enums.RejectionReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RejectionFeedbackRepository extends JpaRepository<RejectionFeedback, Long> {
    List<RejectionFeedback> findByProfessionalId(Long professionalId);
    
    // Conta quantas vezes um profissional rejeitou por cada motivo
    @Query("SELECT r.reason, COUNT(r) FROM RejectionFeedback r WHERE r.professional.id = :professionalId GROUP BY r.reason")
    List<Object[]> countByReasonForProfessional(Long professionalId);
}