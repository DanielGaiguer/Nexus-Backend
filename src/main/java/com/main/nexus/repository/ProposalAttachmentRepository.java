package com.main.nexus.repository;

import com.main.nexus.model.ProposalAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalAttachmentRepository extends JpaRepository<ProposalAttachment, Long> {

    // LGPD -- exclusão de conta do profissional: o texto da proposta é registro
    // contratual (mantido), mas o arquivo binário do anexo é apagado.
    List<ProposalAttachment> findByProposalProfessionalId(Long professionalId);
}
