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

// Espelho de ScreeningQuestion, sem nada que amarre a uma vaga: sem etapa, sem projeto, sem
// resposta apontando pra cá. É molde, não questão respondível.
//
// Sem `active`: ScreeningQuestion precisa de soft-delete porque ScreeningAnswer referencia a
// linha por FK, e apagar quebraria o histórico de quem já respondeu. Aqui nada referencia estas
// linhas -- a aplicação COPIA os campos para ScreeningQuestion novas -- então editar um molde
// pode simplesmente substituir a lista inteira.
@Entity
@Table(name = "tb_assessment_template_question")
public class AssessmentTemplateQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assessment_template_id", nullable = false)
    private AssessmentTemplate assessmentTemplate;

    // Só MULTIPLE_CHOICE e ESSAY -- LIKERT_SCALE pertence ao inventário fixo de plataforma e
    // VIDEO_RESPONSE só existe em etapa VIDEO (ver AssessmentTemplateService.applyQuestionFields).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScreeningQuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false)
    private Integer orderIndex = 0;

    @ElementCollection
    @CollectionTable(
        name = "tb_assessment_template_question_option",
        joinColumns = @JoinColumn(name = "template_question_id")
    )
    @OrderColumn(name = "option_order")
    @Column(name = "option_text", columnDefinition = "TEXT")
    private List<String> options = new ArrayList<>();

    @Column
    private Integer correctOptionIndex;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AssessmentTemplate getAssessmentTemplate() {
        return assessmentTemplate;
    }

    public void setAssessmentTemplate(AssessmentTemplate assessmentTemplate) {
        this.assessmentTemplate = assessmentTemplate;
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
