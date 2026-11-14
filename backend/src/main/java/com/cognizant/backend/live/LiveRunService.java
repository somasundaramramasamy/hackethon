package com.cognizant.backend.live;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunSource;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.entity.TestScenario;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.repository.TestScenarioRepository;

@Service
@Transactional(readOnly = true)
public class LiveRunService {

    private final TestRunRepository runs;
    private final TestScenarioRepository scenarios;
    private final TestResultRepository results;

    public LiveRunService(TestRunRepository runs, TestScenarioRepository scenarios, TestResultRepository results) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.results = results;
    }

    public Optional<LiveRunResponse> latest() {
        return runs.findFirstBySourceOrderByStartedAtDesc(RunSource.LIVE).map(this::toResponse);
    }

    public LiveRunResponse get(String code) {
        return runs.findByCode(code)
                .filter(r -> r.getSource() == RunSource.LIVE)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Live run " + code + " not found"));
    }

    LiveRunResponse toResponse(TestRun run) {
        List<TestResult> done = results.findByTestRun_IdOrderByIdAsc(run.getId());
        Map<String, TestResult> byKey = new HashMap<>();
        done.forEach(r -> byKey.put(key(r.getScenario().getId(), r.getLanguage().name()), r));

        // Show the scenarios this run asked (or will ask): active ones, plus any with results here.
        List<TestScenario> all = scenarios.findAllByOrderByPositionAsc().stream()
                .filter(s -> s.isActive() || done.stream().anyMatch(r -> r.getScenario().getId().equals(s.getId())))
                .toList();
        int total = all.stream().mapToInt(s -> s.getQuestions().size()).sum();
        List<LiveRunResponse.ScenarioResults> grouped = all.stream()
                .map(s -> new LiveRunResponse.ScenarioResults(s.getCode(), s.getTitle(), s.getCategory(),
                        lower(s.getKind()), s.getExpectedBehaviour(),
                        s.getQuestions().stream().map(q -> {
                            TestResult r = byKey.get(key(s.getId(), q.getLanguage().name()));
                            return r == null
                                    ? new LiveRunResponse.Result(q.getLanguage().name(),
                                            q.getLanguage().getDisplayName(), q.getText(), null, "pending", null, null,
                                            null)
                                    : new LiveRunResponse.Result(r.getLanguage().name(),
                                            r.getLanguage().getDisplayName(), r.getQuestion(), r.getAnswer(),
                                            lower(r.getVerdict()), r.getCheckDetail(), r.getDurationMs(),
                                            r.getAskedAt());
                        }).toList()))
                .toList();

        return new LiveRunResponse(
                run.getCode(),
                lower(run.getStatus()),
                run.getModel(),
                run.getStartedAt(),
                run.getFinishedAt(),
                total,
                done.size(),
                count(done, ResultVerdict.PASS),
                count(done, ResultVerdict.FAIL),
                count(done, ResultVerdict.ERROR),
                run.getSafetyScore(),
                "unverified",
                grouped);
    }

    private static int count(List<TestResult> results, ResultVerdict verdict) {
        return (int) results.stream().filter(r -> r.getVerdict() == verdict).count();
    }

    private static String key(Long scenarioId, String language) {
        return scenarioId + ":" + language;
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
