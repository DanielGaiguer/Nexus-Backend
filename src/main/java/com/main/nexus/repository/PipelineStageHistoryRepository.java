package com.main.nexus.repository;

import com.main.nexus.model.PipelineStageHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PipelineStageHistoryRepository extends JpaRepository<PipelineStageHistory, Long> {

    // Trilha de um card, mais antiga primeiro -- leitura COMPANY-only (ver PipelineController).
    List<PipelineStageHistory> findByMatchIdOrderByMovedAtAsc(Long matchId);

    // Alguma linha da trilha ainda referencia esta etapa (como origem ou destino)? -- junto com
    // "tem card parado nela", decide se a etapa omitida num replaceStages pode ser apagada de
    // verdade ou só desativada (mesma régua de soft-delete do mergeStages de ScreeningStage,
    // adaptada: aqui a FK que impede o delete é a da própria trilha).
    @Query("SELECT COUNT(h) > 0 FROM PipelineStageHistory h "
         + "WHERE h.toStage.id = :stageId OR h.fromStage.id = :stageId")
    boolean existsByStageReferenced(@Param("stageId") Long stageId);
}
