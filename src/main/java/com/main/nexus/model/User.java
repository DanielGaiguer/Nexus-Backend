/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import com.main.nexus.model.enums.UserType;

@Entity
@Table(name = "tb_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserType type;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(unique = true, length = 64)
    private String linkedinId;

    // Atualizado a cada login bem-sucedido — usado pelo job de inatividade
    // (ProfessionalInactivityService) pra marcar profissional como indisponível
    // depois de 1 mês sem acesso.
    @Column
    private LocalDateTime lastLoginAt;

    // LGPD -- exclusão de conta (DELETE /api/users/me). O pedido não anonimiza
    // na hora: grava aqui o instante do pedido, manda um e-mail com link de
    // confirmação (válido 48h) para o endereço ORIGINAL, e só a confirmação
    // dispara a anonimização. Um novo pedido sobrescreve este campo, invalidando
    // tokens anteriores (o token carrega este timestamp e a confirmação compara).
    @Column
    private LocalDateTime deletionRequestedAt;

    // Preenchido quando a anonimização foi de fato executada. Marca a conta como
    // "removida": bloqueia novo pedido/confirmação e serve de sinal para
    // telas/relatórios. A linha em tb_user NUNCA é apagada (é FK de match,
    // review, mensagem, etc.) -- só anonimizada.
    @Column
    private LocalDateTime anonymizedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserType getType() {
        return type;
    }

    public void setType(UserType type) {
        this.type = type;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getLinkedinId() {
        return linkedinId;
    }

    public void setLinkedinId(String linkedinId) {
        this.linkedinId = linkedinId;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public void setDeletionRequestedAt(LocalDateTime deletionRequestedAt) {
        this.deletionRequestedAt = deletionRequestedAt;
    }

    public LocalDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public void setAnonymizedAt(LocalDateTime anonymizedAt) {
        this.anonymizedAt = anonymizedAt;
    }
}