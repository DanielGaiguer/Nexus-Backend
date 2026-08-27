package com.main.nexus.model;

import com.main.nexus.model.enums.CustomPortalRequestStatus;
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
import java.time.LocalDateTime;

// Solicitacao de interesse de um contratante pela plataforma personalizada.
// Mesmo espirito da fila de aprovacao de empresa (Company.status + AdminController):
// o contratante registra o interesse, o Admin aprova (criando um CustomPortal) ou recusa.
@Entity
@Table(name = "tb_custom_portal_request")
public class CustomPortalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    // Observacao opcional que o contratante escreve ao pedir.
    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomPortalRequestStatus status = CustomPortalRequestStatus.PENDING;

    // Preenchidos na decisao do Admin.
    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column
    private LocalDateTime reviewedAt;

    // Obrigatorio quando status == REJECTED (validado no service).
    @Column(columnDefinition = "TEXT")
    private String decisionReason;

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

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public CustomPortalRequestStatus getStatus() {
        return status;
    }

    public void setStatus(CustomPortalRequestStatus status) {
        this.status = status;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }
}
