package com.main.nexus.model;

import com.main.nexus.model.enums.CompanyInvitationStatus;
import com.main.nexus.model.enums.CompanyMemberRole;
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

// Convite (por e-mail, com token de capability) para um usuário novo entrar numa
// conta empresarial como MEMBER. Mesmo espírito de state machine com decisão do
// CustomPortalRequest. O aceite (POST /api/company/invitations/accept) cria o
// User + o CompanyMember; ver CompanyMemberService.
//
// role é sempre MEMBER por enquanto -- não existe convite de OWNER; virar OWNER é
// a ação separada de transferência de titularidade.
//
// Sem UNIQUE de banco em (company_id, email): o service barra um 2º convite
// PENDING para o mesmo e-mail (409), e um e-mail revogado/expirado pode ser
// convidado de novo -- mesmo padrão do CustomPortalRequest.
@Entity
@Table(name = "tb_company_invitation",
       indexes = {
           @Index(name = "idx_company_invitation_company_status",
                  columnList = "company_id, status"),
           @Index(name = "idx_company_invitation_email_status",
                  columnList = "email, status")
       })
public class CompanyInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // E-mail alvo do convite, como digitado pelo OWNER (só trim, sem lowercase --
    // mesmo tratamento de e-mail do resto do sistema).
    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyMemberRole role = CompanyMemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyInvitationStatus status = CompanyInvitationStatus.PENDING;

    // Quem convidou (o OWNER). Nulo só em cenário degenerado.
    @ManyToOne
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public CompanyMemberRole getRole() {
        return role;
    }

    public void setRole(CompanyMemberRole role) {
        this.role = role;
    }

    public CompanyInvitationStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyInvitationStatus status) {
        this.status = status;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(User invitedBy) {
        this.invitedBy = invitedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
