package com.main.nexus.model;

import com.main.nexus.model.enums.ScreeningStageKind;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

// Molde de teste reutilizável entre vagas -- lógica, português, inglês, ou qualquer prova que a
// empresa queira aplicar em mais de um processo seletivo. É a resposta ao problema que o
// ScreeningQuestionnaire não resolve: ele é 1:1 com Project, então hoje reusar um teste significa
// redigitá-lo no formulário de cada vaga.
//
// ZERO FK PARA PROJECT, de propósito. Aplicar um molde a uma vaga não cria vínculo: COPIA as
// questões para uma ScreeningStage nova daquele questionário (ver
// AssessmentTemplateService.applyToProject). Cópia, e não referência, pelo mesmo princípio que
// governa o módulo inteiro de triagem -- editar não pode ter efeito retroativo em quem já
// respondeu. Se fosse referência, corrigir uma vírgula no molde mudaria a prova debaixo de
// candidatos em andamento em N vagas ao mesmo tempo, e não existe versionamento pra isso.
//
// EXTENSÃO FUTURA (não construída aqui): `company` é NOT NULL porque nesta versão todo molde
// pertence a uma empresa. Se um dia a Nexus fornecer conteúdo pronto de plataforma (bancos de
// lógica/português/inglês mantidos por nós), o caminho é um `ownerType` (PLATFORM | COMPANY) com
// `company` passando a aceitar null -- o mecanismo de aplicação acima não muda. Essa é decisão
// de NEGÓCIO (entrar ou não no ramo de produzir e manter conteúdo avaliativo, com a
// responsabilidade que isso carrega), não de arquitetura, e está deliberadamente adiada.
@Entity
@Table(name = "tb_assessment_template")
public class AssessmentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String title;

    // Reaproveita ScreeningStageKind porque o molde vira uma ScreeningStage, mas na prática só
    // QUESTIONS faz sentido: BEHAVIORAL já é conteúdo fixo de plataforma (BehavioralItem), e
    // VIDEO é uma pergunta sobre a vaga específica, não algo "genérico" a reaplicar. As duas são
    // rejeitadas em AssessmentTemplateService -- o campo existe pra não ter que reintroduzir a
    // coluna se isso mudar.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScreeningStageKind kind = ScreeningStageKind.QUESTIONS;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    // Prazo padrão da etapa gerada, em dias. Vive no molde porque faz parte de "como este teste
    // é aplicado"; a aplicação pode sobrescrever para uma vaga específica.
    @Column(nullable = false)
    private Integer responseDeadlineDays = 3;

    // Soft-delete: aposentar um molde não pode apagar a linha enquanto etapas geradas por ele
    // ainda existirem por aí referenciando-o em ScreeningStage.sourceTemplateId.
    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "assessmentTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 50)
    private List<AssessmentTemplateQuestion> questions = new ArrayList<>();

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ScreeningStageKind getKind() {
        return kind;
    }

    public void setKind(ScreeningStageKind kind) {
        this.kind = kind != null ? kind : ScreeningStageKind.QUESTIONS;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public Integer getResponseDeadlineDays() {
        return responseDeadlineDays;
    }

    public void setResponseDeadlineDays(Integer responseDeadlineDays) {
        this.responseDeadlineDays = responseDeadlineDays;
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

    public List<AssessmentTemplateQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<AssessmentTemplateQuestion> questions) {
        this.questions = questions != null ? questions : new ArrayList<>();
    }
}
