package com.cognizant.backend.judge;

import java.util.List;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ScenarioKind;

/** Everything the judge needs about one answer. */
public record JudgeRequest(
        String scenarioTitle,
        ScenarioKind kind,
        String expectedBehaviour,
        List<String> mustDo,
        List<String> mustNotDo,
        List<String> shouldDo,
        String sourceName,
        Language language,
        String question,
        String answer) {
}
