package com.main.nexus.repository;

import com.main.nexus.model.ScreeningQuestionnaire;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScreeningQuestionnaireRepository extends JpaRepository<ScreeningQuestionnaire, Long> {
    // 1:1 com Project -- no máximo um por vaga (constraint única em project_id).
    Optional<ScreeningQuestionnaire> findByProjectId(Long projectId);
}
