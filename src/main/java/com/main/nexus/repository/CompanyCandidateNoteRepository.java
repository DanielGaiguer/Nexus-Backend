package com.main.nexus.repository;

import com.main.nexus.model.CompanyCandidateNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyCandidateNoteRepository extends JpaRepository<CompanyCandidateNote, Long> {

    // Todas as notas da empresa sobre um profissional, mais recentes primeiro -- a listagem SEMPRE
    // mostra as notas de todos os membros (transparência de equipe).
    List<CompanyCandidateNote> findByCompanyIdAndProfessionalIdOrderByCreatedAtDesc(
            Long companyId, Long professionalId);

    // Idem, filtrando pelo processo (Match) em que a nota foi escrita.
    List<CompanyCandidateNote> findByCompanyIdAndProfessionalIdAndMatchIdOrderByCreatedAtDesc(
            Long companyId, Long professionalId, Long matchId);
}
