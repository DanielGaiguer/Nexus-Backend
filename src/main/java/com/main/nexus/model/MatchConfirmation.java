package com.main.nexus.model;

import com.main.nexus.model.enums.MatchConfirmationPendingReason;
import com.main.nexus.model.enums.MatchConfirmationResolution;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Janela de confirmacao pos-contratacao (1 por match). Abre 30 dias corridos
// apos o fechamento (StatusMatch.MATCHED) e da 7 dias para os dois lados
// responderem "o trabalho foi concluido?" + "qual o valor final combinado?".
// As duas respostas vivem em MatchStatusCheck (uma por lado). Aqui fica o
// estado agregado, o prazo, o valor sugerido para pre-preenchimento, o valor
// definitivo apos reconciliacao e a triagem do Admin.
//
// Camada financeira, Prompt 2. A cobranca em si vem no Prompt 5 -- CONFIRMED
// so marca a contratacao como pronta e chama CommissionService.registerClosedHire.
@Entity
@Table(name = "tb_match_confirmation")
public class MatchConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchConfirmationStatus status = MatchConfirmationStatus.AWAITING_RESPONSES;

    @Column(nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    // openedAt + 7 dias. Depois disso, se os dois lados ainda nao responderam,
    // um job move para PENDING_ADMIN_REVIEW / NO_RESPONSE.
    @Column(nullable = false)
    private LocalDateTime deadline;

    // Sugestao para pre-preencher o formulario (proposta aceita > faixa do
    // projeto). Apenas sugestao -- quem confirma o valor final sao as partes.
    @Column(precision = 12, scale = 2)
    private BigDecimal suggestedAmount;

    // Valor definitivo -- preenchido so quando status == CONFIRMED (media dos
    // dois valores informados, arredondada aos centavos). Base da comissao (Prompt 5).
    @Column(precision = 12, scale = 2)
    private BigDecimal confirmedAmount;

    // Preenchido so quando status == PENDING_ADMIN_REVIEW (mantido depois para
    // histórico -- mostra por que o caso precisou do Admin).
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MatchConfirmationPendingReason pendingReason;

    // Como a janela chegou ao estado terminal. Nulo enquanto nao resolvida.
    // PARTIES_AGREED (automatico, Prompt 2) | ADMIN_SET_VALUE | ADMIN_COULD_NOT_CONFIRM (Prompt 3).
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MatchConfirmationResolution resolution;

    // Quando saiu de AWAITING_RESPONSES (para CONFIRMED / PENDING_ADMIN_REVIEW /
    // CLOSED_NO_CHARGE).
    @Column
    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Triagem do Admin (Prompt 2 -- supervisao, sem resolver valor) ──

    @Column(nullable = false)
    private boolean adminReviewed = false;

    @ManyToOne
    @JoinColumn(name = "reviewed_by_admin")
    private User reviewedByAdmin;

    @Column
    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String adminNote;

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

    public MatchConfirmationStatus getStatus() {
        return status;
    }

    public void setStatus(MatchConfirmationStatus status) {
        this.status = status;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public BigDecimal getSuggestedAmount() {
        return suggestedAmount;
    }

    public void setSuggestedAmount(BigDecimal suggestedAmount) {
        this.suggestedAmount = suggestedAmount;
    }

    public BigDecimal getConfirmedAmount() {
        return confirmedAmount;
    }

    public void setConfirmedAmount(BigDecimal confirmedAmount) {
        this.confirmedAmount = confirmedAmount;
    }

    public MatchConfirmationPendingReason getPendingReason() {
        return pendingReason;
    }

    public void setPendingReason(MatchConfirmationPendingReason pendingReason) {
        this.pendingReason = pendingReason;
    }

    public MatchConfirmationResolution getResolution() {
        return resolution;
    }

    public void setResolution(MatchConfirmationResolution resolution) {
        this.resolution = resolution;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isAdminReviewed() {
        return adminReviewed;
    }

    public void setAdminReviewed(boolean adminReviewed) {
        this.adminReviewed = adminReviewed;
    }

    public User getReviewedByAdmin() {
        return reviewedByAdmin;
    }

    public void setReviewedByAdmin(User reviewedByAdmin) {
        this.reviewedByAdmin = reviewedByAdmin;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }
}
