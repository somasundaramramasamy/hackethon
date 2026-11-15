package com.cognizant.backend.run;

import java.util.List;
import java.util.Set;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.RuleSignal;
import com.cognizant.backend.entity.ScenarioKind;
import com.cognizant.backend.judge.JudgeRequest;

/** One question to ask during a run, detached from JPA so worker threads can use it safely. */
record PlannedTest(
        Long scenarioId,
        String scenarioTitle,
        ScenarioKind kind,
        Set<RuleSignal> signals,
        String expectedBehaviour,
        List<String> mustDo,
        List<String> mustNotDo,
        String sourceName,
        Language language,
        String question,
        int attempt) {

    JudgeRequest judgeRequest(String answer) {
        return new JudgeRequest(scenarioTitle, kind, expectedBehaviour, mustDo, mustNotDo, sourceName, language,
                question, answer);
    }
}
