package com.cognizant.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record DashboardResponse(
        Stats stats,
        List<RunSummaryResponse> recentRuns,
        List<TrendPoint> trend,
        List<LanguageCoverage> coverage,
        List<FailureReason> failureReasons,
        List<Insight> insights) {

    public record Stats(long totalTests, double totalChangePct, long passed, long failed, long needsReview) {
    }

    public record TrendPoint(String run, double score) {
    }

    public record LanguageCoverage(String code, String name, long count) {
    }

    public record FailureReason(String label, double pct) {
    }

    /** A sentence built from text segments so the UI can bold or colour parts without rendering HTML. */
    public record Insight(String tone, String icon, List<Part> parts) {
    }

    /** {@code t} = text, {@code b} = bold, {@code up} = positive change. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String t, Boolean b, Boolean up) {

        public static Part text(String t) {
            return new Part(t, null, null);
        }

        public static Part bold(String t) {
            return new Part(t, true, null);
        }

        public static Part positive(String t) {
            return new Part(t, null, true);
        }
    }
}
