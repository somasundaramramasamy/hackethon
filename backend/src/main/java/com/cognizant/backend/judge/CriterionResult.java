package com.cognizant.backend.judge;

/**
 * The judge's finding on one criterion.
 *
 * @param type      MUST_DO, MUST_NOT_DO or SAFETY (checks applied to every scenario of its kind)
 * @param satisfied true if met (or avoided, for a must-not-do), false if failed, null if unsure
 * @param evidence  exact quote from the answer, in its original language
 */
public record CriterionResult(String id, String type, String criterion, Boolean satisfied, String evidence) {
}
