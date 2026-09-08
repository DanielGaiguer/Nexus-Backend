package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// Trilha de movimentação de um card entre etapas intermediárias do Kanban -- uma linha por
// movimento manual (ver PipelineService.moveCard). É DELIBERADAMENTE separada de MatchHistory:
// aquela está acoplada a StatusMatch (queries como MatchHistoryRepository.findLastMatchedAt
// dependem de toStatus='MATCHED') e é exibida para os DOIS lados do match (MatchController), o que
// vazaria a organização interna do recrutador para o candidato. Esta trilha é COMPANY-only.
//
// A auto-entrada no board (MatchService, quando o match vira COMPANY_INTERESTED/
// PROFESSIONAL_INTERESTED) NÃO grava aqui -- não há um CompanyMember agindo nesse ponto
// (movedBy é NOT NULL) e não é uma movimentação manual.
@Entity
@Table(name = "tb_pipeline_stage_history")
public class PipelineStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    // Null quando o card entrou numa coluna vindo de "fora do board" (não deveria acontecer em
    // moveCard, que exige o card já numa etapa -- mas o schema permite pra não travar evolução).
    @ManyToOne
    @JoinColumn(name = "from_stage_id")
    private PipelineStage fromStage;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_stage_id", nullable = false)
    private PipelineStage toStage;

    @ManyToOne(optional = false)
    @JoinColumn(name = "moved_by", nullable = false)
    private CompanyMember movedBy;

    @Column(nullable = false)
    private LocalDateTime movedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public PipelineStage getFromStage() {
        return fromStage;
    }

    public void setFromStage(PipelineStage fromStage) {
        this.fromStage = fromStage;
    }

    public PipelineStage getToStage() {
        return toStage;
    }

    public void setToStage(PipelineStage toStage) {
        this.toStage = toStage;
    }

    public CompanyMember getMovedBy() {
        return movedBy;
    }

    public void setMovedBy(CompanyMember movedBy) {
        this.movedBy = movedBy;
    }

    public LocalDateTime getMovedAt() {
        return movedAt;
    }

    public void setMovedAt(LocalDateTime movedAt) {
        this.movedAt = movedAt;
    }
}
