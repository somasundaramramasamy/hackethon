package com.cognizant.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.dto.HumanReviewResponse;
import com.cognizant.backend.dto.RunReviewResponse;
import com.cognizant.backend.dto.RunSummaryResponse;
import com.cognizant.backend.dto.SubmitReviewRequest;
import com.cognizant.backend.service.ReviewService;
import com.cognizant.backend.service.TestRunService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/runs")
public class TestRunController {

    private final TestRunService testRunService;
    private final ReviewService reviewService;

    public TestRunController(TestRunService testRunService, ReviewService reviewService) {
        this.testRunService = testRunService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<RunSummaryResponse> listRuns() {
        return testRunService.listRuns();
    }

    @GetMapping("/{runId}/review")
    public RunReviewResponse getReview(@PathVariable String runId) {
        return reviewService.getReview(runId);
    }

    @PostMapping("/{runId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public HumanReviewResponse submitReview(@PathVariable String runId,
            @Valid @RequestBody SubmitReviewRequest request) {
        return reviewService.submitReview(runId, request);
    }
}
