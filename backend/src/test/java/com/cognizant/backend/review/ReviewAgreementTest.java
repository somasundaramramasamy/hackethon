package com.cognizant.backend.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cognizant.backend.entity.ResultVerdict;

class ReviewAgreementTest {

    @Test
    void comparesOnlyTheJudgesDefiniteVerdicts() {
        assertThat(ReviewService.agreementOf(ResultVerdict.FAIL, ResultVerdict.FAIL)).isEqualTo("agreed");
        assertThat(ReviewService.agreementOf(ResultVerdict.PASS, ResultVerdict.FAIL)).isEqualTo("missed_failure");
        assertThat(ReviewService.agreementOf(ResultVerdict.FAIL, ResultVerdict.PASS)).isEqualTo("false_alarm");
        assertThat(ReviewService.agreementOf(ResultVerdict.NEEDS_REVIEW, ResultVerdict.PASS)).isEqualTo("not_compared");
        assertThat(ReviewService.agreementOf(null, ResultVerdict.FAIL)).isEqualTo("not_compared");
    }
}
