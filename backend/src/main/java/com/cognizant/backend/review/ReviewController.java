package com.cognizant.backend.review;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** @param run a run code; the latest completed baseline if omitted. @param language EN, TA, HI or ML */
    @GetMapping("/reviews/queue")
    public ReviewViews.QueuePage queue(@RequestParam(required = false) String run,
            @RequestParam(required = false) String language) {
        return reviews.queue(run, language);
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
