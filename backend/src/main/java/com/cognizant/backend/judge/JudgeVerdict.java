package com.cognizant.backend.judge;

import java.util.List;

import com.cognizant.backend.entity.ResultVerdict;

/**
 * The judge's assessment of one answer. {@code verdict} is decided by Kavasam from the criteria
 * and the judge's own decision, never by the judge's decision alone.
 *
 * @param translation English translation of a non-English answer, or null
 */
public record JudgeVerdict(
        ResultVerdict verdict,
        double confidence,
        String reason,
        List<CriterionResult> criteria,
        String translation) {
}
