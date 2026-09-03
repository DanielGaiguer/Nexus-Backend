package com.main.nexus.model;

import com.main.nexus.model.enums.AuditTargetType;
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

/**
 * Trilha de auditoria (LGPD, accountability): um registro por acesso
 * administrativo a dado pessoal de um usuário. APPEND-ONLY e IMUTÁVEL --
 * a entidade não tem setter nenhum, o repositório não expõe delete/update, e
 * nenhuma rota da aplicação altera ou apaga uma linha. É o que protege a
 * operação numa eventual fiscalização.
 */
@Entity
@Table(name = "tb_data_access_log",
       indexes = {
           @Index(name = "idx_dal_admin", columnList = "admin_user_id, createdAt"),
           @Index(name = "idx_dal_target", columnList = "target_user_id, createdAt"),
           @Index(name = "idx_dal_created", columnList = "createdAt")
       })
public class DataAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    // Nulo quando o acesso não tem um alvo único (ex.: listou todos os usuários).
    @ManyToOne
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditTargetType targetType;

    // Id cru recebido pelo endpoint (professionalId / companyId / conversationId
    // / ...). Guardado mesmo quando targetUser resolve, para rastreabilidade.
    @Column(name = "target_entity_id")
    private Long targetEntityId;

    // O "motivo" -- rótulo da ação, vindo de @AuditDataAccess.action().
    @Column(nullable = false, length = 150)
    private String action;

    @Column(length = 10)
    private String httpMethod;

    @Column(length = 300)
    private String endpoint;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DataAccessLog() {
        // JPA
    }

    public DataAccessLog(User adminUser, User targetUser, AuditTargetType targetType,
                         Long targetEntityId, String action, String httpMethod, String endpoint) {
        this.adminUser = adminUser;
        this.targetUser = targetUser;
        this.targetType = targetType;
        this.targetEntityId = targetEntityId;
        this.action = action;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getAdminUser() {
        return adminUser;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public AuditTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetEntityId() {
        return targetEntityId;
    }

    public String getAction() {
        return action;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
