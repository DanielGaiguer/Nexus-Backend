package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// Configuracao fiscal global do Nexus para emissao de NFS-e (Prompt 6). Tabela
// de uma linha so (id = SINGLETON_ID), editavel pelo Admin -- mesmo padrao de
// CommissionPolicy.
//
// A identidade fiscal em si (CNPJ, Inscricao Municipal, regime, codigo de
// servico, aliquota ISS, certificado) fica no painel do eNotas, referenciada
// aqui so pelo `enotasEmpresaId`. A API key do eNotas fica em variavel de
// ambiente (ENOTAS_API_KEY), nunca no banco.
@Entity
@Table(name = "tb_fiscal_config")
public class FiscalConfig {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    // Id da empresa emitente do Nexus no eNotas.
    @Column(length = 100)
    private String enotasEmpresaId;

    // Descricao padrao do servico na nota (ex.: "Comissao pela intermediacao de
    // contratacao - Nexus").
    @Column(length = 500)
    private String defaultServiceDescription;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "updated_by_admin")
    private User updatedByAdmin;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEnotasEmpresaId() {
        return enotasEmpresaId;
    }

    public void setEnotasEmpresaId(String enotasEmpresaId) {
        this.enotasEmpresaId = enotasEmpresaId;
    }

    public String getDefaultServiceDescription() {
        return defaultServiceDescription;
    }

    public void setDefaultServiceDescription(String defaultServiceDescription) {
        this.defaultServiceDescription = defaultServiceDescription;
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
