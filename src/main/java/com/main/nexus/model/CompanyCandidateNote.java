package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// Nota interna de recrutamento sobre um profissional, escrita por um membro da conta empresarial
// e VISÍVEL SÓ PARA A EMPRESA. Nunca é exposta ao profissional -- ver CompanyCandidateNoteService
// e o comentário em UserDataExportService (a inclusão no export LGPD ainda é uma questão jurídica
// em aberto).
//
// Vínculo: company + professional (amplo) -- a nota vale para qualquer processo futuro com o
// mesmo profissional, não só para um Match. `match` é só contexto opcional ("escrita durante
// ESTE processo").
@Entity
@Table(name = "tb_company_candidate_note")
public class CompanyCandidateNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private Professional professional;

    // Contexto opcional -- em qual processo (Match) a nota foi escrita. Null = nota "geral" sobre
    // o profissional.
    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;

    // Autor (CompanyMember). Fica null se o membro for removido da conta depois -- tb_company_member
    // não tem status REMOVED, a linha é apagada. `authorLabel` preserva a identidade nesse caso.
    @ManyToOne
    @JoinColumn(name = "author_id")
    private CompanyMember author;

    // Nome/e-mail do autor capturado NO MOMENTO da escrita. SEMPRE usado para exibir a nota --
    // nunca releia do CompanyMember ao vivo: o membro pode ter sido removido, ou o e-mail alterado,
    // e a nota tem que continuar mostrando quem a escreveu quando a escreveu.
    @Column(nullable = false, length = 255)
    private String authorLabel;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

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

    public Professional getProfessional() {
        return professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public CompanyMember getAuthor() {
        return author;
    }

    public void setAuthor(CompanyMember author) {
        this.author = author;
    }

    public String getAuthorLabel() {
        return authorLabel;
    }

    public void setAuthorLabel(String authorLabel) {
        this.authorLabel = authorLabel;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
