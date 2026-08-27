package com.main.nexus.model;

import com.main.nexus.model.enums.CustomPortalStatus;
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

// Historico de alteracoes de status de um CustomPortal — util pro Admin auditar
// depois ("por que essa plataforma foi suspensa em maio?"). Uma linha por
// transicao, incluindo a criacao (previousStatus nulo -> ACTIVE).
@Entity
@Table(name = "tb_custom_portal_status_history")
public class CustomPortalStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "custom_portal_id", nullable = false)
    private CustomPortal customPortal;

    // Nulo na linha de criacao do portal.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CustomPortalStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomPortalStatus newStatus;

    @ManyToOne
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    // Motivo/observacao opcional (ex.: "inadimplencia — 2 faturas em aberto").
    @Column(columnDefinition = "TEXT")
    private String note;

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

    public CustomPortalStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(CustomPortalStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public CustomPortalStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(CustomPortalStatus newStatus) {
        this.newStatus = newStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
