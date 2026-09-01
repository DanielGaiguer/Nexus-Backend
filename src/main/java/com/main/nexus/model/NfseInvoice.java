package com.main.nexus.model;

import com.main.nexus.model.enums.NfseInvoiceKind;
import com.main.nexus.model.enums.NfseInvoiceStatus;
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
import java.time.LocalDateTime;

// NFS-e de uma cobranca paga (Prompt 6). 1:1 com a cobranca de origem, que pode
// ser uma comissao (CommissionCharge) OU uma mensalidade de plataforma
// (PortalSubscriptionCharge) -- exatamente uma das duas fica preenchida.
// A emissao e feita pelo eNotas; aqui guardamos o id/numero da nota e os links de
// download do PDF/XML, mais o motivo quando falha.
@Entity
@Table(name = "tb_nfse_invoice")
public class NfseInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Uma das duas cobrancas abaixo fica preenchida (ver getKind()).
    @OneToOne
    @JoinColumn(name = "commission_charge_id", unique = true)
    private CommissionCharge commissionCharge;

    @OneToOne
    @JoinColumn(name = "portal_subscription_charge_id", unique = true)
    private PortalSubscriptionCharge portalSubscriptionCharge;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NfseInvoiceStatus status = NfseInvoiceStatus.PENDING;

    // Id da NFe no eNotas.
    @Column(name = "enotas_id", length = 60)
    private String enotasId;

    @Column(length = 30)
    private String numero;

    @Column(length = 15)
    private String serie;

    @Column(length = 500)
    private String linkPdf;

    @Column(length = 500)
    private String linkXml;

    @Column(length = 60)
    private String codigoVerificacao;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column
    private LocalDateTime issuedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CommissionCharge getCommissionCharge() {
        return commissionCharge;
    }

    public void setCommissionCharge(CommissionCharge commissionCharge) {
        this.commissionCharge = commissionCharge;
    }

    public PortalSubscriptionCharge getPortalSubscriptionCharge() {
        return portalSubscriptionCharge;
    }

    public void setPortalSubscriptionCharge(PortalSubscriptionCharge portalSubscriptionCharge) {
        this.portalSubscriptionCharge = portalSubscriptionCharge;
    }

    public NfseInvoiceKind getKind() {
        return portalSubscriptionCharge != null
                ? NfseInvoiceKind.PORTAL_SUBSCRIPTION
                : NfseInvoiceKind.COMMISSION;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public NfseInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(NfseInvoiceStatus status) {
        this.status = status;
    }

    public String getEnotasId() {
        return enotasId;
    }

    public void setEnotasId(String enotasId) {
        this.enotasId = enotasId;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public String getLinkPdf() {
        return linkPdf;
    }

    public void setLinkPdf(String linkPdf) {
        this.linkPdf = linkPdf;
    }

    public String getLinkXml() {
        return linkXml;
    }

    public void setLinkXml(String linkXml) {
        this.linkXml = linkXml;
    }

    public String getCodigoVerificacao() {
        return codigoVerificacao;
    }

    public void setCodigoVerificacao(String codigoVerificacao) {
        this.codigoVerificacao = codigoVerificacao;
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

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }
}
