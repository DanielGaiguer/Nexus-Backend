package com.main.nexus.model;

import com.main.nexus.model.enums.SidebarSection;
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
import java.time.LocalDateTime;

// Última vez que um usuário abriu uma seção da sidebar do "Padrão B" -- 1 linha
// por (usuário, seção). O badge daquela seção conta os eventos (notificações)
// mais novos que este `seenAt`; abrir a seção empurra o `seenAt` para agora e
// zera o badge. Bem mais simples que um "não lido" por item como em mensagens --
// aqui basta a seção como um todo. Ver SidebarBadgeService.
@Entity
@Table(name = "tb_section_view",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_section_view_user_section",
               columnNames = {"user_id", "section"}))
public class SectionView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SidebarSection section;

    @Column(nullable = false)
    private LocalDateTime seenAt = LocalDateTime.now();

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

    public SidebarSection getSection() {
        return section;
    }

    public void setSection(SidebarSection section) {
        this.section = section;
    }

    public LocalDateTime getSeenAt() {
        return seenAt;
    }

    public void setSeenAt(LocalDateTime seenAt) {
        this.seenAt = seenAt;
    }
}
