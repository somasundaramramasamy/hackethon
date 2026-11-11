package com.cognizant.backend.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cognizant.backend.dto.HumanReviewResponse;
import com.cognizant.backend.dto.RunReviewResponse;
import com.cognizant.backend.dto.SubmitReviewRequest;
import com.cognizant.backend.entity.HumanReview;
import com.cognizant.backend.entity.ReviewAction;
import com.cognizant.backend.entity.ReviewCase;
import com.cognizant.backend.entity.ReviewStatus;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.repository.HumanReviewRepository;
import com.cognizant.backend.repository.ReviewCaseRepository;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    /** Used until authentication exists and the reviewer comes from the signed-in user. */
    static final String UNNAMED_REVIEWER = "Unnamed reviewer";

    private final ReviewCaseRepository reviewCaseRepository;
    private final HumanReviewRepository humanReviewRepository;

    public ReviewService(ReviewCaseRepository reviewCaseRepository, HumanReviewRepository humanReviewRepository) {
        this.reviewCaseRepository = reviewCaseRepository;
        this.humanReviewRepository = humanReviewRepository;
    }

    public RunReviewResponse getReview(String code) {
        ReviewCase reviewCase = findCase(code);
        return RunReviewResponse.from(reviewCase,
                humanReviewRepository.findByReviewCase_CodeOrderByCreatedAtDesc(code));
    }

    /** Records the reviewer's decision and moves the case to its next state. */
    @Transactional
    public HumanReviewResponse submitReview(String code, SubmitReviewRequest request) {
        ReviewCase reviewCase = findCase(code);
        ReviewAction action = ReviewAction.valueOf(request.action().toUpperCase(Locale.ROOT));
        SubmitReviewRequest.Assessment a = request.assessment();

        reviewCase.applyReviewAction(action);
        HumanReview review = humanReviewRepository.save(new HumanReview(
                reviewCase,
                a.safety(),
                a.groundedness(),
                a.language(),
                a.verdict(),
                request.comment().strip(),
                action,
                StringUtils.hasText(request.reviewer()) ? request.reviewer().strip() : UNNAMED_REVIEWER));
        return HumanReviewResponse.from(review);
    }

    public long countPendingReviews() {
        return reviewCaseRepository.countByReviewStatus(ReviewStatus.NEEDS_REVIEW);
    }

    private ReviewCase findCase(String code) {
        return reviewCaseRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Run " + code + " not found"));
    }
}
