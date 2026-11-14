package com.cognizant.backend.controller;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Exercises controller → service → repository → H2 with the seeded sample data. */
@SpringBootTest(properties = {
        // Override any real keys on the machine (env vars outrank application.properties).
        "GEMINI_API_KEY=", "ANTHROPIC_API_KEY=",
        "spring.ai.google.genai.api-key=test-placeholder", "spring.ai.anthropic.api-key="})
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

    private static final String CASE = "TR-2026-0142";

    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardAggregatesSeededRuns() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalTests").value(2076))
                .andExpect(jsonPath("$.stats.passed").value(1655))
                .andExpect(jsonPath("$.stats.failed").value(285))
                .andExpect(jsonPath("$.stats.needsReview").value(136))
                .andExpect(jsonPath("$.recentRuns", hasSize(5)))
                .andExpect(jsonPath("$.recentRuns[0].id").value("TR-1048"))
                .andExpect(jsonPath("$.recentRuns[0].status").value("completed"))
                .andExpect(jsonPath("$.trend", hasSize(10)))
                .andExpect(jsonPath("$.trend[9].run").value("TR-1048"))
                .andExpect(jsonPath("$.coverage", hasSize(3)))
                .andExpect(jsonPath("$.failureReasons", hasSize(3)))
                .andExpect(jsonPath("$.failureReasons[0].label").value("Incomplete / Unsafe Advice"))
                .andExpect(jsonPath("$.insights", hasSize(4)));
    }

    @Test
    void listsRunsNewestFirst() throws Exception {
        mvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].id").value("TR-1048"))
                .andExpect(jsonPath("$[0].languages[1]").value("TA"));
    }

    @Test
    void returnsReviewCase() throws Exception {
        mvc.perform(get("/api/runs/{id}/review", CASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CASE))
                .andExpect(jsonPath("$.reviewStatus").value("needs_review"))
                .andExpect(jsonPath("$.verification").value("unverified"))
                .andExpect(jsonPath("$.responses", hasSize(3)))
                .andExpect(jsonPath("$.responses[1].name").value("Tamil"))
                .andExpect(jsonPath("$.responses[0].translation").doesNotExist())
                .andExpect(jsonPath("$.sources[0].short").value("H&S"))
                .andExpect(jsonPath("$.history", hasSize(0)));
    }

    @Test
    void unknownCaseIs404ProblemDetail() throws Exception {
        mvc.perform(get("/api/runs/{id}/review", "TR-NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Run TR-NOPE not found"));
    }

    @Test
    void approvingVerifiesCaseAndRecordsHistory() throws Exception {
        mvc.perform(post("/api/runs/{id}/review", CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("approve", "Safe in all three languages.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(greaterThan(0)))
                .andExpect(jsonPath("$.action").value("approve"))
                .andExpect(jsonPath("$.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.reviewer").value("Somasundaram R."));

        mvc.perform(get("/api/runs/{id}/review", CASE))
                .andExpect(jsonPath("$.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.verification").value("verified"))
                .andExpect(jsonPath("$.history", hasSize(1)));

        mvc.perform(get("/api/reviews/pending/count"))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void retestMovesPipelineToStepFive() throws Exception {
        mvc.perform(post("/api/runs/{id}/review", CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("retest", "Retest after the Tamil wording fix.")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/runs/{id}/review", CASE))
                .andExpect(jsonPath("$.pipelineStep").value(5))
                .andExpect(jsonPath("$.reviewStatus").value("retest_queued"));
    }

    @Test
    void rejectsBlankCommentAndUnknownAction() throws Exception {
        mvc.perform(post("/api/runs/{id}/review", CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("delete_everything", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment").value("must not be blank"))
                .andExpect(jsonPath("$.errors.action").value("must be approve, request_fix or retest"));

        mvc.perform(get("/api/reviews/pending/count"))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void aiEndpointsReport503WithoutApiKey() throws Exception {
        mvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistant.provider").value("gemini"))
                .andExpect(jsonPath("$.assistant.configured").value(false))
                .andExpect(jsonPath("$.judge.model").value("claude-opus-5-5"));

        mvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Chest pain?\",\"language\":\"EN\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI Not Configured"));
    }

    @Test
    void liveRunNeedsGeminiKeyAndStartsEmpty() throws Exception {
        mvc.perform(get("/api/scenarios")).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/live-runs/latest")).andExpect(status().isNoContent());
        mvc.perform(post("/api/live-runs"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("GEMINI_API_KEY")));
    }

    private static String body(String action, String comment) {
        return """
                {
                  "assessment": {
                    "safety": "Acceptable",
                    "groundedness": "Acceptable",
                    "language": "Needs Improvement",
                    "verdict": "Needs Fix"
                  },
                  "comment": "%s",
                  "action": "%s",
                  "reviewer": "Somasundaram R."
                }
                """.formatted(comment, action);
    }
}
