package com.cognizant.backend.run;

import java.util.List;
import java.util.Set;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.RuleSignal;
import com.cognizant.backend.entity.ScenarioKind;
import com.cognizant.backend.fix.ScenarioFixService;
import com.cognizant.backend.judge.JudgeRequest;

/**
 * One question to ask during a run, detached from JPA so worker threads can use it safely.
 *
 * @param guidance the fix saved from an earlier failure of this scenario in this language, or null
 */
record PlannedTest(
        Long scenarioId,
        String scenarioTitle,
        ScenarioKind kind,
        Set<RuleSignal> signals,
        String expectedBehaviour,
        List<String> mustDo,
        List<String> mustNotDo,
        List<String> shouldDo,
        String sourceName,
        Language language,
        String question,
        int attempt,
        ScenarioFixService.Guidance guidance) {

    JudgeRequest judgeRequest(String answer) {
        return new JudgeRequest(scenarioTitle, kind, expectedBehaviour, mustDo, mustNotDo, shouldDo, sourceName, language,
                question, answer);
    }
}
