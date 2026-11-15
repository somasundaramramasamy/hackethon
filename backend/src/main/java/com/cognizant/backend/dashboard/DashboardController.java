package com.cognizant.backend.dashboard;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.prompt.PromptService;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.repository.TestScenarioRepository;
import com.cognizant.backend.review.ReviewService;
import com.cognizant.backend.review.ReviewViews;
import com.cognizant.backend.run.RunQueryService;
import com.cognizant.backend.run.RunViews;

/** One call for the dashboard: the latest results by language, the latest fix, and what needs a person. */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final TestRunRepository runs;
    private final TestScenarioRepository scenarios;
    private final RunQueryService queries;
    private final ReviewService reviews;
    private final PromptService prompts;

    public DashboardController(TestRunRepository runs, TestScenarioRepository scenarios, RunQueryService queries,
            ReviewService reviews, PromptService prompts) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.queries = queries;
        this.reviews = reviews;
        this.prompts = prompts;
    }

    /**
     * @param latestBaseline the most recent finished baseline run, or null
     * @param latestRetest   the most recent finished retest with its before/after, or null
     * @param running        the run in progress, or null
     */
    public record Dashboard(RunViews.RunSummary latestBaseline, RunViews.Comparison latestRetest,
            RunViews.RunSummary running, int activePromptVersion, long activeScenarios, long awaitingReview,
            ReviewViews.Agreement agreement, List<RunViews.RunSummary> recentRuns) {
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        var baseline = runs.findFirstByTypeAndStatusOrderByStartedAtDesc(RunType.BASELINE, RunStatus.COMPLETED)
                .map(r -> queries.summary(r.getCode())).orElse(null);
        var retest = runs.findFirstByTypeAndStatusOrderByStartedAtDesc(RunType.RETEST, RunStatus.COMPLETED)
                .map(r -> queries.comparison(r.getCode())).orElse(null);
        var running = runs.findByStatus(RunStatus.RUNNING).stream().findFirst()
                .map(r -> queries.summary(r.getCode())).orElse(null);
        List<RunViews.RunSummary> recent = queries.list().stream().limit(6).toList();
        return new Dashboard(baseline, retest, running, prompts.active().getVersion(),
                scenarios.findAllByActiveTrueOrderByPositionAsc().size(), reviews.queueSize(),
                reviews.agreement().overall(), recent);
    }
}
