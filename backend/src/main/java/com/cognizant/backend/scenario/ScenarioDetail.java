package com.cognizant.backend.scenario;

import java.time.LocalDateTime;
import java.util.List;

/** A scenario as shown in the UI. {@code resultCount} > 0 means it was asked in a live run. */
public record ScenarioDetail(
        String code,
        String title,
        String category,
        String kind,
        String expected,
        List<String> mustDo,
        List<String> mustNotDo,
        String sourceName,
        String sourceUrl,
        boolean active,
        int position,
        List<Question> questions,
        long resultCount,
        LocalDateTime updatedAt) {

    public record Question(String language, String languageName, String text, String reviewedBy) {
    }
}
