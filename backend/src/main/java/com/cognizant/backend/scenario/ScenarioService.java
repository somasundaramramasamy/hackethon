package com.cognizant.backend.scenario;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ScenarioKind;
import com.cognizant.backend.entity.ScenarioQuestion;
import com.cognizant.backend.entity.TestScenario;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestScenarioRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/** Creates, edits, switches on/off, deletes, exports and imports test scenarios. */
@Service
@Transactional(readOnly = true)
public class ScenarioService {

    private static final Pattern CODE_NUMBER = Pattern.compile("SC-(\\d+)");

    private final TestScenarioRepository scenarios;
    private final TestResultRepository results;
    private final Validator validator;

    public ScenarioService(TestScenarioRepository scenarios, TestResultRepository results, Validator validator) {
        this.scenarios = scenarios;
        this.results = results;
        this.validator = validator;
    }

    public List<ScenarioDetail> list(boolean includeInactive) {
        Map<Long, Long> counts = new HashMap<>();
        results.countResultsPerScenario().forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        List<TestScenario> found = includeInactive
                ? scenarios.findAllByOrderByPositionAsc()
                : scenarios.findAllByActiveTrueOrderByPositionAsc();
        return found.stream().map(s -> toDetail(s, counts.getOrDefault(s.getId(), 0L))).toList();
    }

    public ScenarioDetail get(String code) {
        TestScenario s = find(code);
        return toDetail(s, results.countByScenario_Id(s.getId()));
    }

    @Transactional
    public ScenarioDetail create(ScenarioRequest request) {
        TestScenario created = createWithCode(nextCode(), request);
        return toDetail(created, 0);
    }

    @Transactional
    public ScenarioDetail update(String code, ScenarioRequest request) {
        TestScenario s = find(code);
        apply(s, request);
        if (request.active() != null) {
            s.setActive(request.active());
        }
        return toDetail(s, results.countByScenario_Id(s.getId()));
    }

    @Transactional
    public ScenarioDetail setActive(String code, boolean active) {
        TestScenario s = find(code);
        s.setActive(active);
        return toDetail(s, results.countByScenario_Id(s.getId()));
    }

    /** Only scenarios that were never asked can be deleted; others are switched off instead. */
    @Transactional
    public void delete(String code) {
        TestScenario s = find(code);
        long count = results.countByScenario_Id(s.getId());
        if (count > 0) {
            throw new ScenarioInUseException(code, count);
        }
        scenarios.delete(s);
    }

    /** Every scenario in the same format that {@link #importAll} accepts. */
    public List<ScenarioRequest> export() {
        return scenarios.findAllByOrderByPositionAsc().stream().map(ScenarioService::toRequest).toList();
    }

    /**
     * Adds or updates scenarios by code; scenarios without a code are added with a new one.
     * All-or-nothing: if any scenario is invalid, nothing is saved.
     */
    @Transactional
    public ImportResult importAll(List<ScenarioRequest> requests) {
        validate(requests);
        int created = 0;
        int updated = 0;
        List<String> codes = new ArrayList<>();
        for (ScenarioRequest r : requests) {
            var existing = r.code() == null ? null : scenarios.findByCode(r.code()).orElse(null);
            if (existing != null) {
                apply(existing, r);
                existing.setActive(r.active() == null || r.active());
                codes.add(existing.getCode());
                updated++;
            } else {
                TestScenario s = createWithCode(r.code() == null ? nextCode() : r.code(), r);
                codes.add(s.getCode());
                created++;
            }
        }
        return new ImportResult(created, updated, codes);
    }

    private TestScenario createWithCode(String code, ScenarioRequest request) {
        TestScenario s = new TestScenario(code, request.title().strip(), request.category().strip(),
                kind(request.kind()), request.expected().strip(), scenarios.findMaxPosition() + 1);
        apply(s, request);
        s.setActive(request.active() == null || request.active());
        return scenarios.saveAndFlush(s);
    }

    private static void apply(TestScenario s, ScenarioRequest r) {
        // Questions are always stored in EN, TA, HI order.
        Map<Language, ScenarioRequest.QuestionInput> ordered = new EnumMap<>(Language.class);
        ordered.putAll(r.questions());
        List<ScenarioQuestion> questions = ordered.entrySet().stream()
                .map(e -> new ScenarioQuestion(e.getKey(), e.getValue().text().strip(),
                        blankToNull(e.getValue().reviewedBy())))
                .toList();
        s.update(r.title().strip(), r.category().strip(), kind(r.kind()), r.expected().strip(),
                clean(r.mustDo()), clean(r.mustNotDo()), blankToNull(r.sourceName()), blankToNull(r.sourceUrl()),
                questions);
    }

    String nextCode() {
        int max = scenarios.findAllCodes().stream()
                .map(CODE_NUMBER::matcher)
                .filter(Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max()
                .orElse(0);
        return String.format(Locale.ROOT, "SC-%02d", max + 1);
    }

    private void validate(List<ScenarioRequest> requests) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            for (ConstraintViolation<ScenarioRequest> v : validator.validate(requests.get(i))) {
                errors.putIfAbsent("[" + i + "]." + v.getPropertyPath(), v.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidScenarioException("The file has " + errors.size() + " problem(s); nothing was imported.",
                    errors);
        }
    }

    private TestScenario find(String code) {
        return scenarios.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Scenario " + code + " not found"));
    }

    static ScenarioDetail toDetail(TestScenario s, long resultCount) {
        return new ScenarioDetail(s.getCode(), s.getTitle(), s.getCategory(), lower(s.getKind()),
                s.getExpectedBehaviour(), s.getMustDo(), s.getMustNotDo(), s.getSourceName(), s.getSourceUrl(),
                s.isActive(), s.getPosition(),
                s.getQuestions().stream()
                        .map(q -> new ScenarioDetail.Question(q.getLanguage().name(),
                                q.getLanguage().getDisplayName(), q.getText(), q.getReviewedBy()))
                        .toList(),
                resultCount, s.getUpdatedAt());
    }

    private static ScenarioRequest toRequest(TestScenario s) {
        Map<Language, ScenarioRequest.QuestionInput> questions = new EnumMap<>(Language.class);
        s.getQuestions().forEach(q -> questions.put(q.getLanguage(),
                new ScenarioRequest.QuestionInput(q.getText(), q.getReviewedBy())));
        return new ScenarioRequest(s.getCode(), s.getTitle(), s.getCategory(), lower(s.getKind()),
                s.getExpectedBehaviour(), s.getMustDo(), s.getMustNotDo(), s.getSourceName(), s.getSourceUrl(),
                s.isActive(), questions);
    }

    private static ScenarioKind kind(String value) {
        return ScenarioKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
    }

    private static List<String> clean(List<String> items) {
        return items == null ? List.of()
                : items.stream().filter(StringUtils::hasText).map(String::strip).toList();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
