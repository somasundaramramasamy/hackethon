package com.cognizant.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.cognizant.backend.entity.HumanReview;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.LanguageResult;
import com.cognizant.backend.entity.ReviewAction;
import com.cognizant.backend.entity.ReviewCase;
import com.cognizant.backend.entity.ReviewStatus;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.TestRun;

/** JPA slice: only repositories and H2, no seeder or web layer. */
@DataJpaTest
class RepositoryTest {

    @Autowired
    private TestRunRepository testRuns;

    @Autowired
    private ReviewCaseRepository reviewCases;

    @Autowired
    private HumanReviewRepository humanReviews;

    @Test
    void ordersRunsNewestFirstAndKeepsLanguageResults() {
        TestRun older = new TestRun("TR-1", "Suite A", RunStatus.COMPLETED, LocalDateTime.parse("2026-11-01T09:00"), 80);
        older.addLanguageResult(new LanguageResult(Language.EN, 10, 9, 1));
        TestRun newer = new TestRun("TR-2", "Suite B", RunStatus.COMPLETED, LocalDateTime.parse("2026-11-02T09:00"), 85);
        newer.addLanguageResult(new LanguageResult(Language.EN, 10, 8, 1));
        newer.addLanguageResult(new LanguageResult(Language.TA, 10, 6, 3));
        testRuns.saveAll(java.util.List.of(older, newer));

        var runs = testRuns.findAllByOrderByStartedAtDesc();

        assertThat(runs).extracting(TestRun::getCode).containsExactly("TR-2", "TR-1");
        assertThat(runs.get(0).getTotal()).isEqualTo(20);
        assertThat(runs.get(0).getPassed()).isEqualTo(14);
        assertThat(runs.get(0).getLanguages()).containsExactly(Language.EN, Language.TA);
        assertThat(testRuns.findByCode("TR-1")).isPresent();
    }

    @Test
    void findsReviewsByCaseCodeAndCountsPending() {
        ReviewCase pending = reviewCases.save(newCase("TR-A"));
        ReviewCase approved = newCase("TR-B");
        approved.applyReviewAction(ReviewAction.APPROVE);
        reviewCases.save(approved);

        humanReviews.save(new HumanReview(pending, "Acceptable", "Acceptable", "Acceptable", "Safe",
                "Looks fine", ReviewAction.APPROVE, "Tester"));

        assertThat(reviewCases.countByReviewStatus(ReviewStatus.NEEDS_REVIEW)).isEqualTo(1);
        assertThat(humanReviews.findByReviewCase_CodeOrderByCreatedAtDesc("TR-A")).hasSize(1);
        assertThat(humanReviews.findByReviewCase_CodeOrderByCreatedAtDesc("TR-B")).isEmpty();
    }

    private static ReviewCase newCase(String code) {
        return new ReviewCase(code, "Title", "Category", "Model", LocalDateTime.parse("2026-11-10T10:00"),
                "Prompt", 80, "Needs Human Review", "Reason");
    }
}
