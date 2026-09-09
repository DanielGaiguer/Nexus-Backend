package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Uma resposta do profissional para uma ScreeningQuestion, dentro de uma ScreeningInvitation. Só
// os campos relevantes pro tipo da questão são preenchidos (selectedOptionIndex para
// MULTIPLE_CHOICE, essayText para ESSAY) -- ver ScreeningInvitationService.submit. Sem nota
// manual por questão -- a empresa lê as respostas (com a referência automática de
// MULTIPLE_CHOICE) e decide de forma binária pela etapa inteira, não questão por questão (ver
// ScreeningInvitation.companyDecisionComment).
@Entity
@Table(name = "tb_screening_answer")
public class ScreeningAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "screening_invitation_id", nullable = false)
    private ScreeningInvitation screeningInvitation;

    @ManyToOne
    @JoinColumn(name = "screening_question_id", nullable = false)
    private ScreeningQuestion screeningQuestion;

    // MULTIPLE_CHOICE
    @Column
    private Integer selectedOptionIndex;

    // ESSAY
    @Column(columnDefinition = "TEXT")
    private String essayText;

    // VIDEO_RESPONSE -- forma canônica: o endpoint AUTENTICADO do objeto no bucket privado
    // (ver SupabaseStorageService), não uma URL que o browser consiga abrir sozinho. Toda
    // exibição passa por uma signed URL de curta validade gerada sob guard. Diferente dos
    // outros campos de resposta, este é preenchido ANTES do submit: o vídeo sobe primeiro, num
    // endpoint próprio, e o submit apenas confere que existe.
    @Column(length = 500)
    private String videoUrl;

    // Duração APROXIMADA, medida pelo MediaRecorder no client -- informativa, não confiável.
    // O backend não abre o container do arquivo pra conferir (dependência nova, desproporcional
    // nesta versão): o que ele garante de verdade é só o teto de TAMANHO. Ver
    // nexus.screening.video.max-size-bytes.
    @Column
    private Integer videoDurationSeconds;

    // Computado no submit (MULTIPLE_CHOICE) -- null para ESSAY, que não tem gabarito.
    @Column
    private Boolean correct;

    // Instrumentado silenciosamente no client (tempo entre a primeira interação nessa questão
    // e a submissão final) e enviado junto no submit -- não há autosave incremental.
    @Column
    private Integer timeSpentSeconds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ScreeningInvitation getScreeningInvitation() {
        return screeningInvitation;
    }

    public void setScreeningInvitation(ScreeningInvitation screeningInvitation) {
        this.screeningInvitation = screeningInvitation;
    }

    public ScreeningQuestion getScreeningQuestion() {
        return screeningQuestion;
    }

    public void setScreeningQuestion(ScreeningQuestion screeningQuestion) {
        this.screeningQuestion = screeningQuestion;
    }

    public Integer getSelectedOptionIndex() {
        return selectedOptionIndex;
    }

    public void setSelectedOptionIndex(Integer selectedOptionIndex) {
        this.selectedOptionIndex = selectedOptionIndex;
    }

    public String getEssayText() {
        return essayText;
    }

    public void setEssayText(String essayText) {
        this.essayText = essayText;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Integer getVideoDurationSeconds() {
        return videoDurationSeconds;
    }

    public void setVideoDurationSeconds(Integer videoDurationSeconds) {
        this.videoDurationSeconds = videoDurationSeconds;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(Integer timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }
}
