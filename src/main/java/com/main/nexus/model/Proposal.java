package com.main.nexus.model;

import com.main.nexus.model.enums.ProposalStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Proposta de execução enviada pelo profissional para um Project do tipo PROJECT com
// acceptsProposals = true. Coexiste com o fluxo de match bilateral (Match/StatusMatch) -- ver
// ProposalService para as regras de como as duas coisas se encaixam.
@Entity
@Table(name = "tb_proposal")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne
    @JoinColumn(name = "professional_id", nullable = false)
    private Professional professional;

    @Column(nullable = false)
    private Double proposedValue;

    @Column(nullable = false)
    private Integer estimatedDays;

    private LocalDate proposedStartDate;

    private LocalDate proposedDeliveryDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String relevantExperience;

    @ManyToMany
    @JoinTable(
        name = "tb_proposal_skill",
        joinColumns = @JoinColumn(name = "proposal_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private List<Skill> skills = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String deliverables;

    // Lista ordenada -- a ordem em que as etapas aparecem importa (é um plano de execução).
    @ElementCollection
    @CollectionTable(
        name = "tb_proposal_execution_step",
        joinColumns = @JoinColumn(name = "proposal_id")
    )
    @OrderColumn(name = "step_order")
    @Column(name = "step", columnDefinition = "TEXT")
    private List<String> executionSteps = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String paymentTerms;

    @Column(nullable = false)
    private Integer validityDays;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(columnDefinition = "TEXT")
    private String questionsForCompany;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProposalAttachment> attachments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status = ProposalStatus.PENDING;

    // Snapshot do score de compatibilidade (MatchService.getScore) no momento do envio --
    // nunca recalculado do zero, só reaproveitado do motor de match já existente.
    @Column(nullable = false)
    private Double matchScoreAtSubmission;

    // Diferencia a recusa automática por esgotamento de vagas (quando outra proposta do
    // mesmo projeto é aceita e o projeto atinge maxPositions) de uma recusa ativa da empresa --
    // mesmo status REJECTED nos dois casos, mas o texto exibido/enviado por e-mail é distinto.
    @Column(nullable = false)
    private Boolean autoRejectedPositionFilled = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Professional getProfessional() {
        return professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public Double getProposedValue() {
        return proposedValue;
    }

    public void setProposedValue(Double proposedValue) {
        this.proposedValue = proposedValue;
    }

    public Integer getEstimatedDays() {
        return estimatedDays;
    }

    public void setEstimatedDays(Integer estimatedDays) {
        this.estimatedDays = estimatedDays;
    }

    public LocalDate getProposedStartDate() {
        return proposedStartDate;
    }

    public void setProposedStartDate(LocalDate proposedStartDate) {
        this.proposedStartDate = proposedStartDate;
    }

    public LocalDate getProposedDeliveryDate() {
        return proposedDeliveryDate;
    }

    public void setProposedDeliveryDate(LocalDate proposedDeliveryDate) {
        this.proposedDeliveryDate = proposedDeliveryDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRelevantExperience() {
        return relevantExperience;
    }

    public void setRelevantExperience(String relevantExperience) {
        this.relevantExperience = relevantExperience;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public String getDeliverables() {
        return deliverables;
    }

    public void setDeliverables(String deliverables) {
        this.deliverables = deliverables;
    }

    public List<String> getExecutionSteps() {
        return executionSteps;
    }

    public void setExecutionSteps(List<String> executionSteps) {
        this.executionSteps = executionSteps != null ? executionSteps : new ArrayList<>();
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getQuestionsForCompany() {
        return questionsForCompany;
    }

    public void setQuestionsForCompany(String questionsForCompany) {
        this.questionsForCompany = questionsForCompany;
    }

    public List<ProposalAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ProposalAttachment> attachments) {
        this.attachments = attachments != null ? attachments : new ArrayList<>();
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public void setStatus(ProposalStatus status) {
        this.status = status;
    }

    public Double getMatchScoreAtSubmission() {
        return matchScoreAtSubmission;
    }

    public void setMatchScoreAtSubmission(Double matchScoreAtSubmission) {
        this.matchScoreAtSubmission = matchScoreAtSubmission;
    }

    public Boolean getAutoRejectedPositionFilled() {
        return autoRejectedPositionFilled;
    }

    public void setAutoRejectedPositionFilled(Boolean autoRejectedPositionFilled) {
        this.autoRejectedPositionFilled = autoRejectedPositionFilled;
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
