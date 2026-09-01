package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Configuracao global e unica da comissao que o Nexus cobra sobre contratacoes
// fechadas com sucesso (a partir da 4a de cada contratante -- ver CommissionService).
// Tabela de uma linha so: o id e sempre SINGLETON_ID. Hoje e um percentual unico;
// nasce assim de proposito para poder evoluir para faixas por valor no futuro sem
// quebrar quem ja le este registro.
//
// Prompt 1 da camada financeira: aqui esta so a configuracao. O calculo do valor
// da comissao depende da janela de confirmacao (Prompt 2) e a cobranca em si vem
// no Prompt 5 -- nada e cobrado ainda.
@Entity
@Table(name = "tb_commission_policy")
public class CommissionPolicy {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    // Percentual aplicado sobre o valor da contratacao (ex.: 10.00 = 10%),
    // entre 0 e 100. columnDefinition garante o default no ALTER TABLE do
    // ddl-auto, mesmo padrao de Company.type.
    @Column(nullable = false, precision = 5, scale = 2,
            columnDefinition = "DECIMAL(5,2) DEFAULT 10.00")
    private BigDecimal percentage = new BigDecimal("10.00");

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Ultimo admin que alterou o percentual -- so auditoria, pode ser nulo
    // (linha criada automaticamente pelo sistema na primeira leitura).
    @ManyToOne
    @JoinColumn(name = "updated_by_admin")
    private User updatedByAdmin;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUpdatedByAdmin() {
        return updatedByAdmin;
    }

    public void setUpdatedByAdmin(User updatedByAdmin) {
        this.updatedByAdmin = updatedByAdmin;
    }
}
