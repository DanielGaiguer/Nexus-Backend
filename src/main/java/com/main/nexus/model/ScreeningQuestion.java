package com.main.nexus.model;

import com.main.nexus.model.enums.ScreeningQuestionType;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

// Uma questão de uma ScreeningStage -- pode ser qualificatória (ex.: "quantos anos de
// experiência com X"), técnica, ou qualquer outra coisa que o contratante queira perguntar antes
// de avançar com o candidato. options/correctOptionIndex só fazem sentido para
// MULTIPLE_CHOICE -- ESSAY não usa nenhum dos dois (lida pela empresa, sem nota, ver
// ScreeningAnswer). `active=false` é "remover" uma questão que já foi respondida por alguém --
// não apaga a linha (violaria FK de answers já criadas); editar o conteúdo continua livre.
@Entity
@Table(name = "tb_screening_question")
public class ScreeningQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "screening_stage_id", nullable = false)
    private ScreeningStage screeningStage;

    @Column(nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScreeningQuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    // Posição da questão dentro da ScreeningStage -- ScreeningStage.questions é ordenado por
    // este campo (@OrderBy), setado por ScreeningQuestionnaireService ao (re)construir a lista.
    @Column(nullable = false)
    private Integer orderIndex = 0;

    // Lista ordenada -- só populada para MULTIPLE_CHOICE, mesmo padrão de
    // Proposal.executionSteps.
    @ElementCollection
    @CollectionTable(
        name = "tb_screening_question_option",
        joinColumns = @JoinColumn(name = "question_id")
    )
    @OrderColumn(name = "option_order")
    @Column(name = "option_text", columnDefinition = "TEXT")
    private List<String> options = new ArrayList<>();

    // Índice (0-based) da alternativa correta em `options` -- só MULTIPLE_CHOICE. Nunca
    // serializado pro profissional antes da submissão (ver ScreeningAttemptQuestionDTO).
    @Column
    private Integer correctOptionIndex;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ScreeningStage getScreeningStage() {
        return screeningStage;
    }

    public void setScreeningStage(ScreeningStage screeningStage) {
        this.screeningStage = screeningStage;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public ScreeningQuestionType getType() {
        return type;
    }

    public void setType(ScreeningQuestionType type) {
        this.type = type;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    public Integer getCorrectOptionIndex() {
        return correctOptionIndex;
    }

    public void setCorrectOptionIndex(Integer correctOptionIndex) {
        this.correctOptionIndex = correctOptionIndex;
    }
}
