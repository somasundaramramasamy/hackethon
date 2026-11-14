package com.cognizant.backend.live;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ScenarioKind;

/** One question to ask during a run, detached from JPA so it can be used on the runner thread. */
record PlannedTest(Long scenarioId, ScenarioKind kind, Language language, String question) {
}
