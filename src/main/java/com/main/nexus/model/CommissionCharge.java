package com.main.nexus.model;

import com.main.nexus.model.enums.CommissionChargeStatus;
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

// Uma cobranca de comissao no Mercado Pago (camada financeira, Prompt 5).
// 1:1 com a MatchConfirmation que a originou (status CONFIRMED e fora das 3
// contratacoes gratuitas). O valor da comissao = baseAmount * percentage / 100,
// congelado no momento da criacao.
@Entity
@Table(name = "tb_commission_charge")
public class CommissionCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "match_confirmation_id", nullable = false, unique = true)
    private MatchConfirmation matchConfirmation;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Valor final confirmado da contratacao (base de calculo).
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount;

    // Snapshot do percentual da CommissionPolicy no momento da criacao.
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    // Comissao a cobrar = baseAmount * percentage / 100.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommissionChargeStatus status = CommissionChargeStatus.PENDING;

    // Id do pagamento no Mercado Pago (preenchido a partir de PROCESSING).
    @Column(name = "mp_payment_id", length = 60)
    private String mpPaymentId;

    // status_detail do MP (ex.: "cc_rejected_insufficient_amount") -- diagnostico.
    @Column(length = 80)
    private String mpStatusDetail;

    // Texto legivel do motivo da ultima falha, para exibir ao contratante/Admin.
    @Column(length = 300)
    private String failureReason;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column
    private LocalDateTime lastAttemptAt;

    @Column
    private LocalDateTime paidAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MatchConfirmation getMatchConfirmation() {
        return matchConfirmation;
    }

    public void setMatchConfirmation(MatchConfirmation matchConfirmation) {
        this.matchConfirmation = matchConfirmation;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public CommissionChargeStatus getStatus() {
        return status;
    }

    public void setStatus(CommissionChargeStatus status) {
        this.status = status;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
    }

    public void setMpPaymentId(String mpPaymentId) {
        this.mpPaymentId = mpPaymentId;
    }

    public String getMpStatusDetail() {
        return mpStatusDetail;
    }

    public void setMpStatusDetail(String mpStatusDetail) {
        this.mpStatusDetail = mpStatusDetail;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
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

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
