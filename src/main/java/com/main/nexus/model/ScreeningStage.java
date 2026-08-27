package com.main.nexus.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

// Uma etapa do processo seletivo de uma vaga -- um ScreeningQuestionnaire tem uma ou mais etapas
// em sequência (ScreeningQuestionnaire.stages, ordenadas por orderIndex). O profissional só é
// convidado pra etapa N+1 depois que a empresa aprova a N (ver
// ScreeningInvitationService.checkGate/approveStage). `active=false` é "remover" uma etapa que já
// tem candidato em andamento nela -- não apaga a linha (violaria FK de invitations/answers já
// criadas), só passa a ser ignorada no cálculo de "próxima etapa" pra quem ainda não chegou nela;
// editar o conteúdo continua livre a qualquer momento, sem efeito retroativo em quem já respondeu.
@Entity
@Table(name = "tb_screening_stage")
public class ScreeningStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "screening_questionnaire_id", nullable = false)
    private ScreeningQuestionnaire screeningQuestionnaire;

    // Posição da etapa dentro do questionário -- setado por ScreeningQuestionnaireService ao
    // (re)construir a lista.
    @Column(nullable = false)
    private Integer orderIndex = 0;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    // Prazo de resposta em dias, contado a partir do momento em que o profissional é convidado
    // pra esta etapa (ver ScreeningInvitation.sentAt/deadlineAt).
    @Column(nullable = false)
    private Integer responseDeadlineDays;

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "screeningStage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 50)
    private List<ScreeningQuestion> questions = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ScreeningQuestionnaire getScreeningQuestionnaire() {
        return screeningQuestionnaire;
    }

    public void setScreeningQuestionnaire(ScreeningQuestionnaire screeningQuestionnaire) {
        this.screeningQuestionnaire = screeningQuestionnaire;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public List<ScreeningQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<ScreeningQuestion> questions) {
        this.questions = questions != null ? questions : new ArrayList<>();
    }
}
