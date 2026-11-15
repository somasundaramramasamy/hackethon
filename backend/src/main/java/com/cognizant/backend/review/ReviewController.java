package com.cognizant.backend.review;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.run.RunQueryService;
import com.cognizant.backend.run.RunViews;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviews;
    private final RunQueryService queries;

    public ReviewController(ReviewService reviews, RunQueryService queries) {
        this.reviews = reviews;
        this.queries = queries;
    }

    @GetMapping("/reviews/queue")
    public List<ReviewViews.QueueItem> queue() {
        return reviews.queue();
    }

    @GetMapping("/reviews/queue/count")
    public Map<String, Long> queueCount() {
        return Map.of("count", reviews.queueSize());
    }

    @GetMapping("/reviews/agreement")
    public ReviewViews.AgreementReport agreement() {
        return reviews.agreement();
    }

    @PostMapping("/results/{id}/review")
    public ResponseEntity<RunViews.ResultDetail> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        reviews.review(id, request);
        return ResponseEntity.ok(queries.result(id));
    }
}
