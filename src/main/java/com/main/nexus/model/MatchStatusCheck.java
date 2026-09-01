package com.main.nexus.model;

import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Resposta de UM lado (contratante ou profissional) na janela de confirmacao
// pos-contratacao (30 dias) -- ver MatchConfirmation e MatchStatusCheckService.
// Antes era 1 por match, so o contratante respondia e so guardava o outcome;
// agora e 1 por match POR LADO (unique match_id + answered_by) e carrega tambem
// o valor final que aquele lado informou.
@Entity
@Table(name = "tb_match_status_check",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_status_check_match_side",
               columnNames = {"match_id", "answered_by"}))
public class MatchStatusCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(name = "answered_by", nullable = false, length = 20)
    private AuthorType answeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchOutcome outcome;

    // Valor final que este lado informou (salario do 1o mes para JOB, valor do
    // projeto/entrega para PROJECT). Nulo quando o lado respondeu que NAO houve
    // trabalho (DID_NOT_WORK_OUT / NO_CONTACT_YET).
    @Column(name = "final_amount", precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(nullable = false)
    private LocalDateTime answeredAt = LocalDateTime.now();

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

    public AuthorType getAnsweredBy() {
        return answeredBy;
    }

    public void setAnsweredBy(AuthorType answeredBy) {
        this.answeredBy = answeredBy;
    }

    public MatchOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(MatchOutcome outcome) {
        this.outcome = outcome;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }
}
