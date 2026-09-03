package com.main.nexus.model;

import com.main.nexus.model.enums.ConsentSource;
import com.main.nexus.model.enums.ConsentType;
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

// Log de eventos (append-only) de decisoes de consentimento. Cada concessao,
// recusa ou re-aceite e uma LINHA NOVA -- nunca se altera uma linha existente.
// O estado atual de uma finalidade e a linha mais recente por (user, type).
// LGPD exige poder PROVAR o que foi consentido e quando; alem disso e o mesmo
// padrao "linha como evento" ja usado nos badges da sidebar (tb_section_view).
@Entity
@Table(name = "tb_user_consent",
       indexes = {
           @Index(name = "idx_user_consent_user_type", columnList = "user_id, type"),
           @Index(name = "idx_user_consent_lookup",
                  columnList = "user_id, type, granted, document_version")
       })
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConsentType type;

    // true = aceito / opt-in; false = recusado / revogado.
    @Column(nullable = false)
    private Boolean granted;

    // Qual documento o numero de versao abaixo referencia. Para TERMS_OF_USE e o
    // proprio Termo; para as duas finalidades opcionais e a Politica de
    // Privacidade (e onde essas finalidades sao descritas), para saber que texto
    // o titular viu quando decidiu.
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 30)
    private LegalDocumentType documentType;

    // Versao do documento em vigor no momento desta decisao. Nulo so em cenarios
    // degenerados (nenhum documento seedado ainda).
    @Column(name = "document_version")
    private Integer documentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentSource source;

    // Best-effort, para trilha de consentimento. Pode ser nulo.
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // Timestamp imutavel da decisao.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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

    public ConsentType getType() {
        return type;
    }

    public void setType(ConsentType type) {
        this.type = type;
    }

    public Boolean getGranted() {
        return granted;
    }

    public void setGranted(Boolean granted) {
        this.granted = granted;
    }

    public LegalDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(LegalDocumentType documentType) {
        this.documentType = documentType;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer documentVersion) {
        this.documentVersion = documentVersion;
    }

    public ConsentSource getSource() {
        return source;
    }

    public void setSource(ConsentSource source) {
        this.source = source;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
