package com.cognizant.backend.dto;

import java.time.Instant;

import com.cognizant.backend.entity.HumanReview;

public record HumanReviewResponse(
        Long id,
        String caseId,
        SubmitReviewRequest.Assessment assessment,
        String comment,
        String action,
        String reviewer,
        Instant savedAt,
        String reviewStatus) {

    public static HumanReviewResponse from(HumanReview r) {
        return new HumanReviewResponse(
                r.getId(),
                r.getReviewCase().getCode(),
                new SubmitReviewRequest.Assessment(r.getSafety(), r.getGroundedness(), r.getLanguageQuality(),
                        r.getVerdict()),
                r.getComment(),
                ApiValues.of(r.getAction()),
                r.getReviewer(),
                r.getCreatedAt(),
                ApiValues.of(r.getReviewCase().getReviewStatus()));
    }
}
