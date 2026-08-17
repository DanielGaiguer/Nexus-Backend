package com.main.nexus.model;

import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CompanyRejectionReason;
import com.main.nexus.model.enums.ProfessionalRejectionReason;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.List;

@Entity
@Table(name = "tb_rejection_feedback")
public class RejectionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthorType rejectedBy;

    // Preenchido apenas quando rejectedBy == PROFESSIONAL
    // Cria outra tabela de relacionamento, com ID e ENUM
    // Uma lista de elementos
    @ElementCollection(targetClass = ProfessionalRejectionReason.class)
    //Tipo do enum
    @Enumerated(EnumType.STRING)
    // Cria uma tabela auxiliar
    @CollectionTable(
        name = "tb_professional_rejection_reasons",
        joinColumns = @JoinColumn(name = "rejection_id")
    )
    @Column(name = "reason", nullable = false, length = 40)
    private List<ProfessionalRejectionReason> professionalReasons;

    // Preenchido apenas quando rejectedBy == COMPANY
    // Cria outra tabela de relacionamento, com ID e ENUM
    @ElementCollection(targetClass = CompanyRejectionReason.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "tb_company_rejection_reasons",
        joinColumns = @JoinColumn(name = "rejection_id")
    )
    @Column(name = "reason", nullable = false, length = 40)
    private List<CompanyRejectionReason> companyReasons;

    // Texto livre opcional, escrito por quem rejeitou, complementando os motivos
    // selecionados acima — exibido no card de match recusado como "Observação sobre a rejeição".
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public AuthorType getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(AuthorType rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public List<ProfessionalRejectionReason> getProfessionalReasons() {
        return professionalReasons;
    }

    public void setProfessionalReasons(List<ProfessionalRejectionReason> professionalReasons) {
        this.professionalReasons = professionalReasons;
    }

    public List<CompanyRejectionReason> getCompanyReasons() {
        return companyReasons;
    }

    public void setCompanyReasons(List<CompanyRejectionReason> companyReasons) {
        this.companyReasons = companyReasons;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Atalhos para navegação 

    public Professional getProfessional() {
        return match != null ? match.getProfessional() : null;
    }

    public Project getProject() {
        return match != null ? match.getProject() : null;
    }
}