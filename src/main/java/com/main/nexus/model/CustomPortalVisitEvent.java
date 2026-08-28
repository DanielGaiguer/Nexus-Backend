package com.main.nexus.model;

import com.main.nexus.model.enums.CustomPortalEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// Evento de visita na página pública de uma plataforma personalizada. Log
// append-only — alimenta o dashboard "Análises" do contratante. Gravado por um
// endpoint público (sem sessão), então tudo aqui vem de cliente anônimo:
// visitorId é um UUID que o próprio browser gera e guarda no localStorage.
@Entity
@Table(
    name = "tb_custom_portal_visit_event",
    indexes = {
        @Index(name = "idx_cpve_portal_created", columnList = "custom_portal_id, createdAt")
    })
public class CustomPortalVisitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "custom_portal_id", nullable = false)
    private CustomPortal customPortal;

    @Column(nullable = false, length = 40)
    private String visitorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomPortalEventType type;

    // "/" para a home, "/vaga/{id}" para o detalhe de uma vaga.
    @Column(length = 200)
    private String path;

    // Preenchido quando o evento é numa vaga específica.
    @Column
    private Long opportunityId;

    // Só em SESSION_END — quanto tempo o visitante ficou na plataforma.
    @Column
    private Integer durationSeconds;

    // Host do document.referrer (ex.: "google.com"), ou nulo p/ acesso direto.
    @Column(length = 150)
    private String referrerHost;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public CustomPortalEventType getType() {
        return type;
    }

    public void setType(CustomPortalEventType type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Long getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(Long opportunityId) {
        this.opportunityId = opportunityId;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getReferrerHost() {
        return referrerHost;
    }

    public void setReferrerHost(String referrerHost) {
        this.referrerHost = referrerHost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
