package com.main.nexus.repository;

import com.main.nexus.model.Proposal;
import com.main.nexus.model.enums.ProposalStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    List<Proposal> findByProjectId(Long projectId);

    List<Proposal> findByProjectIdAndStatus(Long projectId, ProposalStatus status);

    List<Proposal> findByProfessionalId(Long professionalId);

    Optional<Proposal> findByProjectIdAndProfessionalIdAndStatus(
            Long projectId, Long professionalId, ProposalStatus status);

    List<Proposal> findByStatusAndExpiresAtBefore(ProposalStatus status, LocalDateTime expiresAtBefore);
}
