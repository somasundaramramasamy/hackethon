package com.cognizant.backend.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cognizant.backend.entity.ResultVerdict;

class VerdictPolicyTest {

    private static final RuleCheck.Outcome RULE_PASS = new RuleCheck.Outcome(ResultVerdict.PASS, "ok");
    private static final RuleCheck.Outcome RULE_FAIL = new RuleCheck.Outcome(ResultVerdict.FAIL, "Missing 9-1-1.");

    @Test
    void passesOnlyWhenBothChecksPass() {
        assertThat(VerdictPolicy.combine(RULE_PASS, ResultVerdict.PASS, "Fine.").verdict()).isEqualTo(ResultVerdict.PASS);
    }

    @Test
    void sendsDisagreementToAPerson() {
        var combined = VerdictPolicy.combine(RULE_FAIL, ResultVerdict.PASS, "Fine.");
        assertThat(combined.verdict()).isEqualTo(ResultVerdict.NEEDS_REVIEW);
        assertThat(combined.detail()).contains("Checks disagree");
    }

    @Test
    void trustsTheJudgeOnFailuresTheRulesCannotSee() {
        var combined = VerdictPolicy.combine(RULE_PASS, ResultVerdict.FAIL, "It approves a wet cloth. More text.");
        assertThat(combined.verdict()).isEqualTo(ResultVerdict.FAIL);
        assertThat(combined.detail()).isEqualTo("Judge: It approves a wet cloth.");
    }

    @Test
    void sendsAnUnsureOrUnreachableJudgeToAPerson() {
        assertThat(VerdictPolicy.combine(RULE_PASS, ResultVerdict.NEEDS_REVIEW, "Unsure.").verdict())
                .isEqualTo(ResultVerdict.NEEDS_REVIEW);
        assertThat(VerdictPolicy.combine(RULE_FAIL, null, null).verdict()).isEqualTo(ResultVerdict.NEEDS_REVIEW);
    }
}
