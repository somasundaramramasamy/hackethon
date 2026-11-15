package com.cognizant.backend.check;

import com.cognizant.backend.entity.ResultVerdict;

/**
 * Combines the rule check and the judge into one verdict. Neither check is trusted alone: when
 * they disagree, or the judge is unsure or unavailable, a person decides.
 */
public final class VerdictPolicy {

    private VerdictPolicy() {
    }

    public record Combined(ResultVerdict verdict, String detail) {
    }

    /**
     * @param judge the judge's verdict, or null if the judge could not be reached
     */
    public static Combined combine(RuleCheck.Outcome rule, ResultVerdict judge, String judgeReason) {
        if (judge == null) {
            return new Combined(ResultVerdict.NEEDS_REVIEW,
                    "Judge unavailable; rule check: " + rule.detail());
        }
        return switch (judge) {
            case FAIL -> new Combined(ResultVerdict.FAIL, "Judge: " + firstSentence(judgeReason));
            case NEEDS_REVIEW -> new Combined(ResultVerdict.NEEDS_REVIEW,
                    "Judge unsure: " + firstSentence(judgeReason));
            case PASS -> rule.verdict() == ResultVerdict.PASS
                    ? new Combined(ResultVerdict.PASS, "Judge and rule check both passed.")
                    : new Combined(ResultVerdict.NEEDS_REVIEW,
                            "Checks disagree: the judge passed it, but the rule check found: " + rule.detail());
            case ERROR -> new Combined(ResultVerdict.NEEDS_REVIEW, "Judge error; rule check: " + rule.detail());
        };
    }

    static String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "no reason given.";
        }
        String t = text.strip();
        int end = t.indexOf(". ");
        return end > 0 && end < 300 ? t.substring(0, end + 1) : (t.length() > 300 ? t.substring(0, 297) + "..." : t);
    }
}
