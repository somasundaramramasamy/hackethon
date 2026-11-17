package com.cognizant.backend.prompt;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.cognizant.backend.entity.AssistantPrompt;

/**
 * A system prompt version as the frontend shows it. {@code status} is lower case, e.g. "proposed".
 *
 * @param sourceRun  the run whose failures this version tries to fix
 * @param retestRuns retests of this version, newest first
 * @param retesting  whether a retest of this version is still running
 */
public record PromptView(
        int version,
        String status,
        String text,
        String summary,
        String rationale,
        Integer basedOnVersion,
        String sourceRun,
        List<String> retestRuns,
        boolean retesting,
        String createdBy,
        Instant createdAt,
        String decidedBy,
        Instant decidedAt,
        String decisionNote) {

    public static PromptView of(AssistantPrompt p, String sourceRunCode, List<String> retestRuns,
            boolean retesting) {
        return new PromptView(p.getVersion(), p.getStatus().name().toLowerCase(Locale.ROOT), p.getText(),
                p.getSummary(), p.getRationale(), p.getBasedOnVersion(), sourceRunCode, retestRuns, retesting,
                p.getCreatedBy(),
                p.getCreatedAt(), p.getDecidedBy(), p.getDecidedAt(), p.getDecisionNote());
    }
}
