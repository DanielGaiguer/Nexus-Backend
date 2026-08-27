package com.main.nexus.model;

import com.main.nexus.model.enums.PendingIntentType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
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
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Uma tentativa de um profissional numa ScreeningStage específica -- vinculada ao par (etapa,
// profissional), não a um Match específico, porque o gate que a cria
// (ScreeningInvitationService.checkGate) pode disparar a partir de professionalShowsInterest,
// professionalAccepts (ambos já têm um Match) ou de ProposalService.submitProposal (que não tem
// nenhum Match ainda). Múltiplas tentativas ao longo do tempo são permitidas -- se uma termina em
// DECLINED/CANCELLED, a próxima vez que o profissional tentar a mesma ação abre uma nova. EXPIRED
// é a exceção: é definitivo, sem nova tentativa (ver ScreeningInvitationService.checkGate).
@Entity
@Table(name = "tb_screening_invitation")
public class ScreeningInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "screening_stage_id", nullable = false)
    private ScreeningStage screeningStage;

    @ManyToOne
    @JoinColumn(name = "professional_id", nullable = false)
    private Professional professional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScreeningInvitationStatus status = ScreeningInvitationStatus.SENT;

    @Column(nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime deadlineAt;

    private LocalDateTime startedAt;

    private LocalDateTime submittedAt;

    // Quando a empresa decidiu (aprovou ou reprovou) -- ver ScreeningInvitationService
    // .approveStage/reproveStage.
    private LocalDateTime decidedAt;

    // Instrumentados no client e enviados de uma vez só no submit.
    private Integer totalTimeSpentSeconds;

    // Quantas vezes o profissional saiu da aba durante a tentativa (visibilitychange/blur) --
    // sinal informativo, visível só para o contratante na avaliação (ver
    // ScreeningInvitationService.toDetailDTO).
    private Integer tabSwitchCount;

    // Percentual de acertos só das questões MULTIPLE_CHOICE -- null se a etapa não tiver nenhuma
    // MULTIPLE_CHOICE. Só uma referência/sugestão pra empresa -- a decisão de aprovar/reprovar é
    // sempre manual, essa nota nunca decide sozinha.
    private Double autoScorePercent;

    // Comentário livre da empresa ao aprovar ou reprovar esta etapa -- opcional.
    @Column(columnDefinition = "TEXT")
    private String companyDecisionComment;

    @OneToMany(mappedBy = "screeningInvitation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScreeningAnswer> answers = new ArrayList<>();

    // RETOMADA AUTOMÁTICA -- a ação do profissional (interesse/aceite) que ficou pendente até o
    // processo de etapas terminar aprovado. Carregada de etapa em etapa (cada nova invitation
    // recebe os mesmos valores da anterior) e só consumida por
    // ScreeningInvitationController.approveStage quando não existe próxima etapa -- proposta
    // (PROPOSAL_SUBMIT) nunca é retomada automaticamente (ver PendingIntentType). Nunca nula em
    // prática -- todo convite nasce de um gate, não existe mais envio manual.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PendingIntentType pendingIntentType;

    @ManyToOne
    @JoinColumn(name = "pending_match_id")
    private Match pendingMatch;

    @ManyToOne
    @JoinColumn(name = "pending_proposal_id")
    private Proposal pendingProposal;

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

    public Professional getProfessional() {
        return professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public ScreeningInvitationStatus getStatus() {
        return status;
    }

    public void setStatus(ScreeningInvitationStatus status) {
        this.status = status;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Integer getTotalTimeSpentSeconds() {
        return totalTimeSpentSeconds;
    }

    public void setTotalTimeSpentSeconds(Integer totalTimeSpentSeconds) {
        this.totalTimeSpentSeconds = totalTimeSpentSeconds;
    }

    public Integer getTabSwitchCount() {
        return tabSwitchCount;
    }

    public void setTabSwitchCount(Integer tabSwitchCount) {
        this.tabSwitchCount = tabSwitchCount;
    }

    public Double getAutoScorePercent() {
        return autoScorePercent;
    }

    public void setAutoScorePercent(Double autoScorePercent) {
        this.autoScorePercent = autoScorePercent;
    }

    public String getCompanyDecisionComment() {
        return companyDecisionComment;
    }

    public void setCompanyDecisionComment(String companyDecisionComment) {
        this.companyDecisionComment = companyDecisionComment;
    }

    public List<ScreeningAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<ScreeningAnswer> answers) {
        this.answers = answers != null ? answers : new ArrayList<>();
    }

    public PendingIntentType getPendingIntentType() {
        return pendingIntentType;
    }

    public void setPendingIntentType(PendingIntentType pendingIntentType) {
        this.pendingIntentType = pendingIntentType;
    }

    public Match getPendingMatch() {
        return pendingMatch;
    }

    public void setPendingMatch(Match pendingMatch) {
        this.pendingMatch = pendingMatch;
    }

    public Proposal getPendingProposal() {
        return pendingProposal;
    }

    public void setPendingProposal(Proposal pendingProposal) {
        this.pendingProposal = pendingProposal;
    }
}
