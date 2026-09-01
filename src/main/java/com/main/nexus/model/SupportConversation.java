package com.main.nexus.model;

import com.main.nexus.model.enums.SupportConversationStatus;
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

// Conversa de suporte entre o Admin e um usuario (profissional ou contratante).
// Separada do chat de match (tb_message/Match) -- ver SupportChatService.
// Pode ser iniciada pelo Admin (openedByAdmin preenchido) ou pelo proprio usuario
// (openedByUser = true, openedByAdmin nulo ate o primeiro admin responder).
// So o Admin encerra.
@Entity
@Table(name = "tb_support_conversation")
public class SupportConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario (profissional/contratante) com quem o Admin esta conversando.
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Nulo quando o chamado foi aberto pelo proprio usuario e nenhum admin
    // respondeu ainda. O primeiro admin a responder passa a ser o dono (ver
    // SupportChatService.recordMessage).
    @ManyToOne
    @JoinColumn(name = "opened_by_admin")
    private User openedByAdmin;

    // true quando o chamado foi aberto pelo usuario (profissional/contratante),
    // nao pelo Admin.
    @Column(name = "opened_by_user", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean openedByUser = false;

    @ManyToOne
    @JoinColumn(name = "closed_by_admin")
    private User closedByAdmin;

    // Assunto curto opcional, definido pelo Admin ao abrir.
    @Column(length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportConversationStatus status = SupportConversationStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime closedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getOpenedByAdmin() {
        return openedByAdmin;
    }

    public void setOpenedByAdmin(User openedByAdmin) {
        this.openedByAdmin = openedByAdmin;
    }

    public boolean isOpenedByUser() {
        return openedByUser;
    }

    public void setOpenedByUser(boolean openedByUser) {
        this.openedByUser = openedByUser;
    }

    public User getClosedByAdmin() {
        return closedByAdmin;
    }

    public void setClosedByAdmin(User closedByAdmin) {
        this.closedByAdmin = closedByAdmin;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public SupportConversationStatus getStatus() {
        return status;
    }

    public void setStatus(SupportConversationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
