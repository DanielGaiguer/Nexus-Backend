package com.main.nexus.model;

import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
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

// Vínculo N:1 (por enquanto) entre um User e a Company em nome da qual ele age,
// com um papel. Base para múltiplos usuários por conta empresarial -- ver
// CompanyAccessService e o backfill em CompanyMemberBackfill.
//
// Company.user (o @OneToOne unique legado) NÃO é removido nesta etapa: ele
// permanece como "OWNER primário / conta de faturamento" e os call sites atuais
// continuam usando ele. A migração dos call sites para CompanyAccessService vem
// numa etapa seguinte.
//
// UNIQUE(company_id, user_id): um usuário não aparece duas vezes na mesma empresa.
// UNIQUE(user_id): decisão v1 -- um User pertence a no máximo UMA empresa. Quando
// (e se) permitirmos consultor multi-conta, essa constraint sai e o token passa a
// carregar a empresa ativa.
@Entity
@Table(name = "tb_company_member",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_company_member_company_user",
                             columnNames = {"company_id", "user_id"}),
           @UniqueConstraint(name = "uk_company_member_user",
                             columnNames = {"user_id"})
       })
public class CompanyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyMemberRole role;

    // v1 só ACTIVE (ver CompanyMemberStatus) -- cobre 100% das linhas hoje
    // (backfill + registro de empresa). O fluxo de convite acrescenta os estados
    // de convite pendente / remoção.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyMemberStatus status = CompanyMemberStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Quem convidou este membro. Nulo para o OWNER criado no cadastro da empresa
    // e para as linhas do backfill.
    @ManyToOne
    @JoinColumn(name = "invited_by")
    private User invitedBy;

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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public CompanyMemberRole getRole() {
        return role;
    }

    public void setRole(CompanyMemberRole role) {
        this.role = role;
    }

    public CompanyMemberStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyMemberStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(User invitedBy) {
        this.invitedBy = invitedBy;
    }
}
