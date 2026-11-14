package com.cognizant.backend.live;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LiveRunController {

    private final LiveTestRunner runner;
    private final LiveRunService service;

    public LiveRunController(LiveTestRunner runner, LiveRunService service) {
        this.runner = runner;
        this.service = service;
    }

    /** Starts running every scenario in every language. Paid/quota-limited model calls. */
    @PostMapping("/api/live-runs")
    public ResponseEntity<LiveRunResponse> start() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.get(runner.start().getCode()));
    }

    /** The most recent live run, or 204 when none has been run yet. */
    @GetMapping("/api/live-runs/latest")
    public ResponseEntity<LiveRunResponse> latest() {
        return service.latest().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/api/live-runs/{runId}")
    public LiveRunResponse get(@PathVariable String runId) {
        return service.get(runId);
    }
}
