package com.cognizant.backend.ai.dto;

import java.util.List;

/** The investigator agent's recommendation for a human reviewer. */
public record InvestigationReport(
        JudgeVerdict.Decision verdict,
        List<String> findings,
        List<String> followUpsAsked,
        List<String> sourcesUsed) {
}
