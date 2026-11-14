package com.cognizant.backend.live;

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

import com.cognizant.backend.repository.TestScenarioRepository;
import com.cognizant.backend.scenario.ScenarioRequest;
import com.cognizant.backend.scenario.ScenarioService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * On startup: closes live runs a restart left unfinished, and loads the starting scenarios from
 * {@code scenarios/default-scenarios.json} into an empty database. After that, scenarios are managed
 * from the Scenarios page. The JSON uses the same format as the page's Export/Import, and its Tamil and
 * Hindi text needs native-speaker review before results are reported.
 */
@Component
public class ScenarioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenarioSeeder.class);

    private final TestScenarioRepository scenarios;
    private final ScenarioService scenarioService;
    private final LiveRunRecorder recorder;
    private final JsonMapper json;
    private final Resource defaults;

    public ScenarioSeeder(TestScenarioRepository scenarios, ScenarioService scenarioService, LiveRunRecorder recorder,
            JsonMapper json, @Value("classpath:scenarios/default-scenarios.json") Resource defaults) {
        this.scenarios = scenarios;
        this.scenarioService = scenarioService;
        this.recorder = recorder;
        this.json = json;
        this.defaults = defaults;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        int stale = recorder.failStaleRuns();
        if (stale > 0) {
            log.warn("Marked {} unfinished live run(s) as failed after restart", stale);
        }
        if (scenarios.count() > 0) {
            return;
        }
        try (InputStream in = defaults.getInputStream()) {
            List<ScenarioRequest> starting = json.readValue(in, new TypeReference<List<ScenarioRequest>>() {
            });
            var result = scenarioService.importAll(starting);
            log.info("Loaded {} starting test scenarios from {}", result.created(), defaults.getFilename());
        }
    }
}
