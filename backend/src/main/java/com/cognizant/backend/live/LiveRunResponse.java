package com.cognizant.backend.live;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** A live run with its progress and every answer, grouped by scenario. */
public record LiveRunResponse(
        String id,
        String status,
        String model,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalTests,
        int completedTests,
        int passed,
        int failed,
        int errors,
        double safetyScore,
        String verification,
        List<ScenarioResults> scenarios) {

    public record ScenarioResults(String code, String title, String category, String kind, String expected,
            List<Result> results) {
    }

    /** {@code verdict} is pass, fail, error or pending (not asked yet). */
    public record Result(String language, String languageName, String question, String answer, String verdict,
            String detail, Long durationMs, Instant askedAt) {
    }
}
