package com.main.nexus.model;

import com.main.nexus.model.enums.PaymentBlockReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// Dados de pagamento de um contratante (camada financeira, Prompt 5). 1:1 com
// Company. O `nexus` NUNCA guarda numero/CVV/validade em texto puro -- so os
// tokens do Mercado Pago (customer_id + card_id) e um resumo do cartao para exibir.
@Entity
@Table(name = "tb_company_billing_profile")
public class CompanyBillingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    // ── Cartao salvo (tokens do Mercado Pago) ────────────────────────
    @Column(name = "mp_customer_id", length = 100)
    private String mpCustomerId;

    @Column(name = "mp_card_id", length = 100)
    private String mpCardId;

    @Column(length = 40)
    private String cardBrand;

    @Column(length = 4)
    private String cardLast4;

    @Column
    private Integer cardExpMonth;

    @Column
    private Integer cardExpYear;

    @Column(length = 150)
    private String cardholderName;

    // ── Bloqueio por pendencia financeira ────────────────────────────
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean paymentBlocked = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentBlockReason blockReason;

    @Column
    private LocalDateTime blockedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public boolean hasCard() {
        return mpCustomerId != null && mpCardId != null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getMpCustomerId() {
        return mpCustomerId;
    }

    public void setMpCustomerId(String mpCustomerId) {
        this.mpCustomerId = mpCustomerId;
    }

    public String getMpCardId() {
        return mpCardId;
    }

    public void setMpCardId(String mpCardId) {
        this.mpCardId = mpCardId;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public Integer getCardExpMonth() {
        return cardExpMonth;
    }

    public void setCardExpMonth(Integer cardExpMonth) {
        this.cardExpMonth = cardExpMonth;
    }

    public Integer getCardExpYear() {
        return cardExpYear;
    }

    public void setCardExpYear(Integer cardExpYear) {
        this.cardExpYear = cardExpYear;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public void setCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
    }

    public Boolean getPaymentBlocked() {
        return paymentBlocked;
    }

    public void setPaymentBlocked(Boolean paymentBlocked) {
        this.paymentBlocked = paymentBlocked;
    }

    public PaymentBlockReason getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(PaymentBlockReason blockReason) {
        this.blockReason = blockReason;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
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
