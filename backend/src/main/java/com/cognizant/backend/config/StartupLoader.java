package com.cognizant.backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.cognizant.backend.agent.InvestigationService;
import com.cognizant.backend.prompt.PromptService;
import com.cognizant.backend.repository.TestScenarioRepository;
import com.cognizant.backend.run.RunRecorder;
import com.cognizant.backend.scenario.ScenarioRequest;
import com.cognizant.backend.scenario.ScenarioService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * On startup: closes runs and investigations a restart left unfinished, creates the first system
 * prompt version, and loads the starting test set into an empty database. After that, scenarios
 * are managed from the Scenarios page.
 */
@Component
public class StartupLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupLoader.class);

    private final RunRecorder runs;
    private final InvestigationService investigations;
    private final PromptService prompts;
    private final TestScenarioRepository scenarios;
    private final ScenarioService scenarioService;
    private final JsonMapper json;
    private final Resource defaults;

    public StartupLoader(RunRecorder runs, InvestigationService investigations, PromptService prompts,
            TestScenarioRepository scenarios, ScenarioService scenarioService, JsonMapper json,
            @Value("classpath:scenarios/default-scenarios.json") Resource defaults) {
        this.runs = runs;
        this.investigations = investigations;
        this.prompts = prompts;
        this.scenarios = scenarios;
        this.scenarioService = scenarioService;
        this.json = json;
        this.defaults = defaults;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        int staleRuns = runs.failStaleRuns();
        int staleInvestigations = investigations.failStale();
        int spotChecks = runs.topUpSpotChecks();
        if (spotChecks > 0) {
            log.info("Picked {} more answer(s) for human spot-checks", spotChecks);
        }
        if (staleRuns + staleInvestigations > 0) {
            log.warn("Closed {} run(s) and {} investigation(s) that had stopped making progress", staleRuns,
                    staleInvestigations);
        }
        if (prompts.seedIfEmpty()) {
            log.info("Created system prompt version 1");
        }
        if (scenarios.count() == 0) {
            try (InputStream in = defaults.getInputStream()) {
                List<ScenarioRequest> starting = json.readValue(in, new TypeReference<List<ScenarioRequest>>() {
                });
                var result = scenarioService.importAll(starting);
                log.info("Loaded {} starting test scenarios from {}", result.created(), defaults.getFilename());
            }
        }
    }
}
