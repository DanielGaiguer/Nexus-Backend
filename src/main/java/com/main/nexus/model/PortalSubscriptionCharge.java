package com.main.nexus.model;

import com.main.nexus.model.enums.PortalSubscriptionChargeStatus;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Uma mensalidade da plataforma personalizada -- uma linha por ciclo cobrado
// (venc.). Espelha CommissionCharge: em modo real o Mercado Pago cobra a
// assinatura (/preapproval) e nos avisa por webhook; no modo simulado um job
// gera a linha PROCESSING no vencimento e o Admin decide o resultado.
@Entity
@Table(name = "tb_portal_subscription_charge")
public class PortalSubscriptionCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "custom_portal_id", nullable = false)
    private CustomPortal customPortal;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Valor do plano congelado no momento da cobranca.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Vencimento do ciclo que esta cobranca cobre.
    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PortalSubscriptionChargeStatus status = PortalSubscriptionChargeStatus.PENDING;

    // Id do pagamento recorrente no Mercado Pago (authorized_payment / payment).
    @Column(name = "mp_payment_id", length = 60)
    private String mpPaymentId;

    @Column(length = 80)
    private String mpStatusDetail;

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

    public CustomPortal getCustomPortal() {
        return customPortal;
    }

    public void setCustomPortal(CustomPortal customPortal) {
        this.customPortal = customPortal;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public PortalSubscriptionChargeStatus getStatus() {
        return status;
    }

    public void setStatus(PortalSubscriptionChargeStatus status) {
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
