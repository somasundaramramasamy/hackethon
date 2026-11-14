package com.cognizant.backend.live;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cognizant.backend.ai.HealthAssistantService;
import com.cognizant.backend.ai.dto.AssistantAnswer;
import com.cognizant.backend.entity.FailureCategory;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.RunSource;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.repository.TestRunRepository;

/**
 * Full live run against a fake assistant (no real model calls). The fake answers safely,
 * except in Tamil for SC-01, so the run must report one cross-language failure.
 */
@SpringBootTest(properties = {
        "GEMINI_API_KEY=test-only", "ANTHROPIC_API_KEY=",
        "spring.ai.google.genai.api-key=test-placeholder", "spring.ai.anthropic.api-key=",
        // Own database: this test commits data from a background thread.
        "spring.datasource.url=jdbc:h2:mem:kavasam-live-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class LiveRunIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRunRepository runs;

    @MockitoBean
    private HealthAssistantService assistant;

    @Test
    void runsEveryScenarioInEveryLanguageAndRecordsResults() throws Exception {
        when(assistant.ask(anyString(), any(Language.class))).thenAnswer(inv -> {
            String question = inv.getArgument(0);
            Language language = inv.getArgument(1);
            Thread.sleep(30); // keep the run alive long enough to check the 409 below
            String answer;
            if (question.contains("வாயுதான்")) { // SC-01 in Tamil: the planted unsafe answer
                answer = "சூடான தண்ணீர் குடித்து ஓய்வெடுங்கள். இது வாயுதான்.";
            } else if (question.contains("nose") || question.contains("headache") || question.contains("மூக்கு")
                    || question.contains("தலைவலி") || question.contains("नाक") || question.contains("सिरदर्द")) {
                answer = "Rest, drink fluids and take regular breaks. These simple steps usually help within days.";
            } else {
                answer = "Call 9-1-1 now. This could be a medical emergency.";
            }
            return new AssistantAnswer(question, language, answer, "fake-model", Instant.now());
        });

        mvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].questions[1].language").value("TA"));

        mvc.perform(post("/api/live-runs"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.totalTests").value(18));

        // Only one run at a time.
        mvc.perform(post("/api/live-runs")).andExpect(status().isConflict());

        TestRun run = waitForFinish();
        assertRun(run);

        mvc.perform(get("/api/live-runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.completedTests").value(18))
                .andExpect(jsonPath("$.passed").value(17))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors").value(0))
                .andExpect(jsonPath("$.verification").value("unverified"))
                .andExpect(jsonPath("$.scenarios", hasSize(6)))
                .andExpect(jsonPath("$.scenarios[0].results[1].language").value("TA"))
                .andExpect(jsonPath("$.scenarios[0].results[1].verdict").value("fail"))
                .andExpect(jsonPath("$.scenarios[0].results[0].verdict").value("pass"));

        // The finished live run also feeds the dashboard and runs list, tagged as live.
        mvc.perform(get("/api/runs"))
                .andExpect(jsonPath("$[0].id").value(run.getCode()))
                .andExpect(jsonPath("$[0].source").value("live"))
                .andExpect(jsonPath("$[0].total").value(18));
    }

    private TestRun waitForFinish() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            var run = runs.findFirstBySourceOrderByStartedAtDesc(RunSource.LIVE).orElseThrow();
            if (run.getStatus() != RunStatus.RUNNING) {
                return run;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Live run did not finish in time");
    }

    private static void assertRun(TestRun run) {
        org.assertj.core.api.Assertions.assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        org.assertj.core.api.Assertions.assertThat(run.getModel()).isEqualTo("gemini-3.5-flash");
        org.assertj.core.api.Assertions.assertThat(run.getSafetyScore()).isEqualTo(94.4);
    }
}
