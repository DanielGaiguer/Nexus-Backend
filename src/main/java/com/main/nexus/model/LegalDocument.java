package com.main.nexus.model;

import com.main.nexus.model.enums.LegalDocumentType;
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

// Uma versao publicada de um documento legal (Termos de Uso ou Politica de
// Privacidade). Cada nova versao e uma linha nova -- as antigas ficam para
// consulta ("ver versoes anteriores"). Exatamente uma linha ativa por tipo;
// essa invariante e do LegalDocumentService (flip da anterior + insert da nova
// na mesma transacao), nao do banco -- MySQL nao tem indice unico parcial.
// Mesmo racional do singleton de CommissionPolicy.
//
// Aviso: o CONTEUDO das versoes seed e uma MINUTA e precisa de revisao juridica
// antes de uso real (ver LegalDocumentSeed e resources/legal/*.md).
@Entity
@Table(name = "tb_legal_document",
       indexes = {
           @Index(name = "idx_legal_document_type_active", columnList = "type, active"),
           @Index(name = "idx_legal_document_type_version", columnList = "type, version")
       })
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LegalDocumentType type;

    // Monotonico por tipo, comeca em 1. Proxima versao = max(version do tipo) + 1.
    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 150)
    private String title;

    // Markdown. Renderizado no frontend com react-markdown (sem HTML bruto).
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    // "O que mudou nesta versao" -- exibido na tela de re-aceite para o usuario
    // saber por que esta sendo perguntado de novo. Nulo na v1 (nao ha o que
    // comparar).
    @Column(length = 500)
    private String summaryOfChanges;

    // Exatamente uma versao ativa por tipo. Ver comentario da classe.
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean active = false;

    @Column(nullable = false)
    private LocalDateTime publishedAt = LocalDateTime.now();

    // Admin que publicou. Nulo na versao seed (criada pelo sistema no bootstrap).
    @ManyToOne
    @JoinColumn(name = "published_by_admin")
    private User publishedByAdmin;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LegalDocumentType getType() {
        return type;
    }

    public void setType(LegalDocumentType type) {
        this.type = type;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSummaryOfChanges() {
        return summaryOfChanges;
    }

    public void setSummaryOfChanges(String summaryOfChanges) {
        this.summaryOfChanges = summaryOfChanges;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public User getPublishedByAdmin() {
        return publishedByAdmin;
    }

    public void setPublishedByAdmin(User publishedByAdmin) {
        this.publishedByAdmin = publishedByAdmin;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
