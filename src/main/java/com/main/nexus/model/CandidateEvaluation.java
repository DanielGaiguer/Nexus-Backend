package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

// Parecer de UM avaliador (membro da conta empresarial) sobre um candidato NESTE processo
// (Match) -- o scorecard colaborativo do Kanban de contratação (Passo 3). Um parecer por avaliador
// por match: o endpoint /evaluations/mine faz upsert nessa chave.
//
// COMPANY-only, como as notas internas -- nunca exposto ao profissional. É subjetivo/humano e
// DISTINTO do Match.matchScore (o score algorítmico 0-100): os dois números nunca se misturam no
// mesmo campo do payload (ver PipelineCardDTO.evaluation vs PipelineCardDTO.matchScore).
//
// UNIQUE(match_id, evaluator_id): no MySQL, múltiplos NULL contam como distintos, então isto
// impede duas linhas do MESMO avaliador no mesmo match, mas permite várias linhas órfãs
// (evaluator_id NULL) de membros já removidos da conta -- e tudo bem, não é um problema real.
@Entity
@Table(name = "tb_candidate_evaluation",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_candidate_evaluation_match_evaluator",
               columnNames = {"match_id", "evaluator_id"}))
public class CandidateEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    // Avaliador (CompanyMember). Fica null se o membro for removido da conta depois --
    // tb_company_member não tem status REMOVED, a linha é apagada. `evaluatorLabel` preserva a
    // identidade nesse caso.
    @ManyToOne
    @JoinColumn(name = "evaluator_id")
    private CompanyMember evaluator;

    // Nome/e-mail do avaliador capturado NO MOMENTO da primeira escrita. SEMPRE usado para exibir
    // -- nunca relido do CompanyMember ao vivo (mesmo padrão de snapshot de autor das notas internas).
    @Column(nullable = false, length = 255)
    private String evaluatorLabel;

    // 1 a 5 -- validado no CandidateEvaluationService (a coluna é um INT simples).
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

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

    public CompanyMember getEvaluator() {
        return evaluator;
    }

    public void setEvaluator(CompanyMember evaluator) {
        this.evaluator = evaluator;
    }

    public String getEvaluatorLabel() {
        return evaluatorLabel;
    }

    public void setEvaluatorLabel(String evaluatorLabel) {
        this.evaluatorLabel = evaluatorLabel;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
