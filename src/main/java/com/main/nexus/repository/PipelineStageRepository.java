package com.main.nexus.repository;

import com.main.nexus.model.PipelineStage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    // Todas as etapas da vaga (ativas e arquivadas), na ordem do board -- o board precisa das
    // arquivadas para exibir cards parados nelas.
    List<PipelineStage> findByProjectIdOrderByOrderIndexAsc(Long projectId);

    // Só as ativas, na ordem -- usada pela auto-entrada (primeira coluna) e como conjunto de
    // destinos válidos de drop.
    List<PipelineStage> findByProjectIdAndActiveTrueOrderByOrderIndexAsc(Long projectId);

    // Primeira coluna ativa da vaga -- alvo da auto-entrada no board.
    Optional<PipelineStage> findFirstByProjectIdAndActiveTrueOrderByOrderIndexAsc(Long projectId);

    // "Esta vaga já tem alguma PipelineStage?" -- trava do seed lazy (não re-semeia se a empresa
    // arquivou todas de propósito).
    long countByProjectId(Long projectId);
}
