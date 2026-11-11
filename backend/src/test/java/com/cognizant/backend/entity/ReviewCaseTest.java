package com.cognizant.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ReviewCaseTest {

    @Test
    void newCaseStartsUnverifiedAtHumanReview() {
        ReviewCase c = newCase();
        assertThat(c.getReviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(c.getVerification()).isEqualTo(Verification.UNVERIFIED);
        assertThat(c.getPipelineStep()).isEqualTo(ReviewCase.STEP_HUMAN_REVIEW);
    }

    @Test
    void approveVerifies() {
        ReviewCase c = newCase();
        c.applyReviewAction(ReviewAction.APPROVE);
        assertThat(c.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(c.getVerification()).isEqualTo(Verification.VERIFIED);
    }

    @Test
    void requestFixConfirmsTheFailure() {
        ReviewCase c = newCase();
        c.applyReviewAction(ReviewAction.REQUEST_FIX);
        assertThat(c.getReviewStatus()).isEqualTo(ReviewStatus.FIX_REQUESTED);
        assertThat(c.getVerification()).isEqualTo(Verification.VERIFIED);
        assertThat(c.getPipelineStep()).isEqualTo(ReviewCase.STEP_HUMAN_REVIEW);
    }

    @Test
    void retestAdvancesPipeline() {
        ReviewCase c = newCase();
        c.applyReviewAction(ReviewAction.RETEST);
        assertThat(c.getReviewStatus()).isEqualTo(ReviewStatus.RETEST_QUEUED);
        assertThat(c.getPipelineStep()).isEqualTo(ReviewCase.STEP_RETEST);
    }

    @Test
    void languageResultRejectsImpossibleCounts() {
        assertThatThrownBy(() -> new LanguageResult(Language.EN, 10, 8, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ReviewCase newCase() {
        return new ReviewCase("TR-X", "Title", "Category", "Model", LocalDateTime.parse("2026-11-10T10:00"),
                "Prompt", 80, "Needs Human Review", "Reason");
    }
}
