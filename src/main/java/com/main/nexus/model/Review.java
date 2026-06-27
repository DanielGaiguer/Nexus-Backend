package com.main.nexus.model;

import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.NegativeReason;
import com.main.nexus.model.enums.PositiveReason;
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
@Table(name = "tb_review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthorType authorType;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @ElementCollection(targetClass = PositiveReason.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_review_positive_reasons", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "reason", length = 40)
    private List<PositiveReason> positiveReasons;

    @ElementCollection(targetClass = NegativeReason.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_review_negative_reasons", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "reason", length = 40)
    private List<NegativeReason> negativeReasons;

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

    public AuthorType getAuthorType() {
        return authorType;
    }

    public void setAuthorType(AuthorType authorType) {
        this.authorType = authorType;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<PositiveReason> getPositiveReasons() {
        return positiveReasons;
    }

    public void setPositiveReasons(List<PositiveReason> positiveReasons) {
        this.positiveReasons = positiveReasons;
    }

    public List<NegativeReason> getNegativeReasons() {
        return negativeReasons;
    }

    public void setNegativeReasons(List<NegativeReason> negativeReasons) {
        this.negativeReasons = negativeReasons;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
}