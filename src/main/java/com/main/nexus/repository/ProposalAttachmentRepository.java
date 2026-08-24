package com.main.nexus.repository;

import com.main.nexus.model.ProposalAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalAttachmentRepository extends JpaRepository<ProposalAttachment, Long> {
}
