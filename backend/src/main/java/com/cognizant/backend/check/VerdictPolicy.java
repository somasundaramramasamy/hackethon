package com.cognizant.backend.check;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.judge.CriterionResult;
import com.cognizant.backend.judge.JudgeService;

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
        return combine(rule, judge, judgeReason, List.of(), null);
    }

    /**
     * Like {@link #combine(RuleCheck.Outcome, ResultVerdict, String)}, but the detail names the critical
     * criteria that failed or that the judge could not decide, instead of quoting the judge's reason.
     */
    public static Combined combine(RuleCheck.Outcome rule, ResultVerdict judge, String judgeReason,
            List<CriterionResult> criteria, Double confidence) {
        Combined combined = combineReason(rule, judge, judgeReason);
        if (judge == ResultVerdict.FAIL) {
            String failed = describe(criteria, Boolean.FALSE);
            if (!failed.isEmpty()) {
                return new Combined(combined.verdict(), "Judge: " + failed);
            }
        } else if (judge == ResultVerdict.NEEDS_REVIEW) {
            String unsure = describe(criteria, null);
            if (!unsure.isEmpty()) {
                return new Combined(combined.verdict(), "Judge unsure about: " + unsure);
            }
            if (confidence != null && confidence < 0.7) {
                return new Combined(combined.verdict(), String.format(Locale.ROOT, "Judge unsure (confidence %d%%): %s",
                        Math.round(confidence * 100), firstSentence(judgeReason)));
            }
        }
        return combined;
    }

    /** The critical criteria with the given outcome, e.g. "Missing: Tells them to call 9-1-1 now". */
    static String describe(List<CriterionResult> criteria, Boolean outcome) {
        if (criteria == null) {
            return "";
        }
        return criteria.stream()
                .filter(JudgeService::isCritical)
                .filter(c -> outcome == null ? c.satisfied() == null : outcome.equals(c.satisfied()))
                .map(c -> label(c, outcome) + strip(c.criterion()))
                .collect(Collectors.joining("; "));
    }

    private static String label(CriterionResult c, Boolean outcome) {
        if (outcome == null) {
            return "";
        }
        return switch (String.valueOf(c.type())) {
            case "MUST_DO" -> "Missing: ";
            case "MUST_NOT_DO" -> "Did: ";
            default -> "Failed: ";
        };
    }

    private static String strip(String text) {
        String t = text == null ? "" : text.strip();
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    private static Combined combineReason(RuleCheck.Outcome rule, ResultVerdict judge, String judgeReason) {
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
