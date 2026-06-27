package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_reputation_metrics")
public class ReputationMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "professional_id", unique = true)
    private Professional professional;

    @OneToOne
    @JoinColumn(name = "company_id", unique = true)
    private Company company;

    private Double technicalCompetence;
    private Double communication;
    private Double reliability;
    private Double punctuality;
    private Double professionalism;
    private Double satisfactionAverage;
    private Double recommendationRate;

    private Double reputationScore;
    private Double confidenceScore;

    @Column(nullable = false)
    private Integer totalReviews = 0;

    @Column(nullable = false)
    private Integer totalReviewsRecent = 0;

    @Column(nullable = false)
    private Integer totalRejectionsReceived = 0;

    private LocalDateTime lastCalculatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Professional getProfessional() {
        return professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Double getTechnicalCompetence() {
        return technicalCompetence;
    }

    public void setTechnicalCompetence(Double technicalCompetence) {
        this.technicalCompetence = technicalCompetence;
    }

    public Double getCommunication() {
        return communication;
    }

    public void setCommunication(Double communication) {
        this.communication = communication;
    }

    public Double getReliability() {
        return reliability;
    }

    public void setReliability(Double reliability) {
        this.reliability = reliability;
    }

    public Double getPunctuality() {
        return punctuality;
    }

    public void setPunctuality(Double punctuality) {
        this.punctuality = punctuality;
    }

    public Double getProfessionalism() {
        return professionalism;
    }

    public void setProfessionalism(Double professionalism) {
        this.professionalism = professionalism;
    }

    public Double getSatisfactionAverage() {
        return satisfactionAverage;
    }

    public void setSatisfactionAverage(Double satisfactionAverage) {
        this.satisfactionAverage = satisfactionAverage;
    }

    public Double getRecommendationRate() {
        return recommendationRate;
    }

    public void setRecommendationRate(Double recommendationRate) {
        this.recommendationRate = recommendationRate;
    }

    public Double getReputationScore() {
        return reputationScore;
    }

    public void setReputationScore(Double reputationScore) {
        this.reputationScore = reputationScore;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public Integer getTotalReviewsRecent() {
        return totalReviewsRecent;
    }

    public void setTotalReviewsRecent(Integer totalReviewsRecent) {
        this.totalReviewsRecent = totalReviewsRecent;
    }

    public Integer getTotalRejectionsReceived() {
        return totalRejectionsReceived;
    }

    public void setTotalRejectionsReceived(Integer totalRejectionsReceived) {
        this.totalRejectionsReceived = totalRejectionsReceived;
    }

    public LocalDateTime getLastCalculatedAt() {
        return lastCalculatedAt;
    }

    public void setLastCalculatedAt(LocalDateTime lastCalculatedAt) {
        this.lastCalculatedAt = lastCalculatedAt;
    }


    
}