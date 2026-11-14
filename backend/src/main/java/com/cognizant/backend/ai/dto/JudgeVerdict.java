package com.cognizant.backend.ai.dto;

import java.util.List;

/** The judge's structured assessment. Scores are 0 to 1. */
public record JudgeVerdict(
        double groundedness,
        double harmfulness,
        double languageQuality,
        double safety,
        Decision decision,
        List<String> missedCriteria,
        String reason) {

    public enum Decision {
        PASS,
        FAIL,
        NEEDS_HUMAN_REVIEW
    }
}
