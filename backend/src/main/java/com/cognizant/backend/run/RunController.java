package com.cognizant.backend.run;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Validated
public class RunController {

    private final RunService runService;
    private final RunQueryService queries;

    public RunController(RunService runService, RunQueryService queries) {
        this.runService = runService;
        this.queries = queries;
    }

    @GetMapping("/runs")
    public List<RunViews.RunSummary> list() {
        return queries.list();
    }

    /** Starts a baseline run in the background; poll GET /api/runs/{code} for progress. */
    @PostMapping("/runs")
    public ResponseEntity<RunViews.RunSummary> start(@Valid @RequestBody StartRunRequest request) {
        var run = runService.startBaseline(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queries.summary(run.getCode()));
    }

    @GetMapping("/runs/{code}")
    public RunViews.RunDetail get(@PathVariable String code) {
        return queries.detail(code);
    }

    @GetMapping("/runs/{code}/comparison")
    public RunViews.Comparison comparison(@PathVariable String code) {
        return queries.comparison(code);
    }

    @GetMapping("/results/{id}")
    public RunViews.ResultDetail result(@PathVariable Long id) {
        return queries.result(id);
    }
}
