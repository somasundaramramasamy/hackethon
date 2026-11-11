package com.cognizant.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A reviewer's assessment and decision on a review case. Kept as an audit trail. */
@Entity
@Table(name = "human_review")
public class HumanReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_case_id", nullable = false)
    private ReviewCase reviewCase;

    @Column(nullable = false, length = 40)
    private String safety;

    @Column(nullable = false, length = 40)
    private String groundedness;

    @Column(nullable = false, length = 40)
    private String languageQuality;

    @Column(nullable = false, length = 40)
    private String verdict;

    @Column(nullable = false, length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewAction action;

    @Column(nullable = false, length = 100)
    private String reviewer;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected HumanReview() {
    }

    public HumanReview(ReviewCase reviewCase, String safety, String groundedness, String languageQuality,
            String verdict, String comment, ReviewAction action, String reviewer) {
        this.reviewCase = reviewCase;
        this.safety = safety;
        this.groundedness = groundedness;
        this.languageQuality = languageQuality;
        this.verdict = verdict;
        this.comment = comment;
        this.action = action;
        this.reviewer = reviewer;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ReviewCase getReviewCase() {
        return reviewCase;
    }

    public String getSafety() {
        return safety;
    }

    public String getGroundedness() {
        return groundedness;
    }

    public String getLanguageQuality() {
        return languageQuality;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getComment() {
        return comment;
    }

    public ReviewAction getAction() {
        return action;
    }

    public String getReviewer() {
        return reviewer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
