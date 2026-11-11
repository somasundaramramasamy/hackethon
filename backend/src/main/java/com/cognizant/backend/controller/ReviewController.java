package com.cognizant.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.dto.CountResponse;
import com.cognizant.backend.service.ReviewService;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Number of cases waiting for a human decision (the sidebar badge). */
    @GetMapping("/pending/count")
    public CountResponse pendingCount() {
        return new CountResponse(reviewService.countPendingReviews());
    }
}
