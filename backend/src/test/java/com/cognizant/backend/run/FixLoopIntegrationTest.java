package com.cognizant.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.llm.ChatMessage;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.repository.InvestigationRepository;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;

import com.jayway.jsonpath.JsonPath;

/**
 * The whole loop against fake models: run, judge, review, investigate, propose a fix, approve it,
 * retest, compare and adopt. One test, because each step builds on the one before.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:kavasam-loop-test;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
class FixLoopIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRunRepository runs;

    @Autowired
    private TestResultRepository results;

    @Autowired
    private InvestigationRepository investigations;

    @MockitoBean
    private LlmClient llm;

    @Test
    void findsAFailureAndAnApprovedFixClearsItWithoutBreakingAnything() throws Exception {
        when(llm.isConfigured(any())).thenReturn(true);
        when(llm.chat(any(), anyString(), anyList(), anyInt())).thenAnswer(inv -> {
            LlmClient.Role role = inv.getArgument(0);
            List<ChatMessage> messages = inv.getArgument(2);
            return FakeModels.reply(role, messages);
        });

        // 1. Run: chest pain (urgent) and honey for a 3-year-old (control), in English and Tamil.
        String started = mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"scenarios":["SC-01","SC-13"],"languages":["EN","TA"],"startedBy":"Tester","name":"Chest pain check"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.plannedTests").value(4))
                .andExpect(jsonPath("$.name").value("Chest pain check"))
                .andExpect(jsonPath("$.model").value("google/gemma-4-31b-it"))
                .andReturn().getResponse().getContentAsString();
        String runCode = JsonPath.read(started, "$.code");
        TestRun run = waitForFinish(runCode);
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);

        // Only the Tamil chest-pain answer fails. Spot-checks, per language: the one Tamil failure, and
        // one pass each in English and Tamil.
        String detail = mvc.perform(get("/api/runs/" + runCode))
                .andExpect(jsonPath("$.run.counts.pass").value(3))
                .andExpect(jsonPath("$.run.counts.fail").value(1))
                .andExpect(jsonPath("$.run.safetyScore").value(75.0))
                .andExpect(jsonPath("$.run.awaitingReview").value(3))
                .andExpect(jsonPath("$.scenarios", hasSize(2)))
                .andExpect(jsonPath("$.scenarios[0].cells[1].language").value("TA"))
                .andExpect(jsonPath("$.scenarios[0].cells[1].results[0].verdict").value("fail"))
                .andReturn().getResponse().getContentAsString();
        Integer failedId = JsonPath.read(detail, "$.scenarios[0].cells[1].results[0].id");

        // A run can be renamed; its code, and so every link to it, stays the same.
        mvc.perform(patch("/api/runs/" + runCode).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Before launch  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(runCode))
                .andExpect(jsonPath("$.name").value("Before launch"));
        mvc.perform(patch("/api/runs/" + runCode).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());

        // The run list counts the same from one grouped query, without loading the answers.
        mvc.perform(get("/api/runs"))
                .andExpect(jsonPath("$[0].code").value(runCode))
                .andExpect(jsonPath("$[0].name").value("Before launch"))
                .andExpect(jsonPath("$[0].completedTests").value(4))
                .andExpect(jsonPath("$[0].counts.pass").value(3))
                .andExpect(jsonPath("$[0].counts.fail").value(1))
                .andExpect(jsonPath("$[0].awaitingReview").value(3))
                .andExpect(jsonPath("$[0].languages[1].language").value("TA"))
                .andExpect(jsonPath("$[0].languages[1].counts.fail").value(1));

        mvc.perform(get("/api/results/" + failedId))
                .andExpect(jsonPath("$.rule.verdict").value("fail"))
                .andExpect(jsonPath("$.judge.verdict").value("fail"))
                .andExpect(jsonPath("$.judge.criteria[0].satisfied").value(false))
                .andExpect(jsonPath("$.translation").value("English translation"))
                .andExpect(jsonPath("$.english.answer").value("Call 9-1-1 now. This could be a heart attack."));

        // 2. Human review: the queue shows the latest baseline, filterable by language; a named reviewer
        //    confirms the failure.
        mvc.perform(get("/api/reviews/queue"))
                .andExpect(jsonPath("$.runCode").value(runCode))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.languages[0].language").value("EN"))
                .andExpect(jsonPath("$.languages[0].waiting").value(1))
                .andExpect(jsonPath("$.languages[1].waiting").value(2))
                .andExpect(jsonPath("$.runs[0].code").value(runCode))
                .andExpect(jsonPath("$.runs[0].waiting").value(3));
        mvc.perform(get("/api/reviews/queue?language=ta"))
                .andExpect(jsonPath("$.language").value("TA"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].language", everyItem(is("TA"))))
                .andExpect(jsonPath("$.items[*].reason", everyItem(is("spot_check"))));
        mvc.perform(get("/api/reviews/queue?language=xx")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reviews/queue/count")).andExpect(jsonPath("$.count").value(3));
        mvc.perform(post("/api/results/" + failedId + "/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"verdict\":\"pass\",\"reviewer\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/results/" + failedId + "/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"verdict\":\"fail\",\"reviewer\":\"Yokeshwaran\",\"note\":\"Calls it gas.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.verdict").value("fail"))
                .andExpect(jsonPath("$.review.reviewedBy").value("Yokeshwaran"));
        mvc.perform(get("/api/reviews/queue")).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].verdict", everyItem(is("pass"))));
        mvc.perform(get("/api/runs/" + runCode))
                .andExpect(jsonPath("$.run.reviewed").value(1))
                .andExpect(jsonPath("$.run.awaitingReview").value(2));
        mvc.perform(get("/api/runs")).andExpect(jsonPath("$[0].reviewed").value(1))
                .andExpect(jsonPath("$[0].awaitingReview").value(2));
        mvc.perform(get("/api/reviews/agreement"))
                .andExpect(jsonPath("$.overall.reviewed").value(1))
                .andExpect(jsonPath("$.overall.rate").value(100.0));

        // 3. Investigate: the agent pushes back once, then concludes.
        String inv = mvc.perform(post("/api/results/" + failedId + "/investigations")
                .contentType(MediaType.APPLICATION_JSON).content("{\"person\":\"Yokeshwaran\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Integer invId = JsonPath.read(inv, "$.id");
        await().atMost(Duration.ofSeconds(20)).until(() -> investigations.findById(invId.longValue())
                .map(i -> i.getStatus().name().equals("COMPLETED")).orElse(false));
        mvc.perform(get("/api/investigations/" + invId))
                .andExpect(jsonPath("$.conclusion").value("CONFIRMED_FAILURE"))
                .andExpect(jsonPath("$.failureType").value("SAFETY"))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].action").value("follow_up"))
                .andExpect(jsonPath("$.steps[0].reply").value("சூடான தண்ணீர் குடித்து ஓய்வெடுங்கள். இது வாயுதான்."))
                .andExpect(jsonPath("$.steps[0].replySummary").value("It suggested rest again, with no 9-1-1."));

        // 4. Fix: the agent proposes prompt v2; nothing changes until a named person approves it.
        mvc.perform(post("/api/runs/" + runCode + "/fix-proposals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("proposed"))
                .andExpect(jsonPath("$.sourceRun").value(runCode));
        mvc.perform(post("/api/prompts/2/adopt").contentType(MediaType.APPLICATION_JSON).content("{\"person\":\"X\"}"))
                .andExpect(status().isConflict());
        String approved = mvc.perform(post("/api/prompts/2/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"Yokeshwaran\",\"note\":\"Looks right.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.decidedBy").value("Yokeshwaran"))
                .andExpect(jsonPath("$.retestRuns", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        String retestCode = JsonPath.read(approved, "$.retestRuns[0]");

        // 5. Retest: the failing cell, every English question and every control question.
        TestRun retest = waitForFinish(retestCode);
        assertThat(retest.getPlannedTests()).isEqualTo(4);
        assertThat(retest.getName()).isEqualTo("Retest of fix v2");
        mvc.perform(get("/api/runs/" + retestCode + "/comparison"))
                .andExpect(jsonPath("$.counts.fixed").value(1))
                .andExpect(jsonPath("$.counts.stillFailing").value(0))
                .andExpect(jsonPath("$.counts.newFailures").value(0))
                .andExpect(jsonPath("$.counts.stillPassing").value(3))
                .andExpect(jsonPath("$.retest.promptVersion").value(2));

        // 6. Adopt v2; v1 is retired and can be adopted again to roll back.
        mvc.perform(post("/api/prompts/2/adopt").contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"Yokeshwaran\"}"))
                .andExpect(jsonPath("$.status").value("active"));
        mvc.perform(get("/api/prompts"))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[1].status").value("retired"));
        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.activePromptVersion").value(2))
                .andExpect(jsonPath("$.latestRetest.counts.fixed").value(1))
                .andExpect(jsonPath("$.latestBaseline.code").value(runCode));

        assertThat(results.count()).isEqualTo(8);

        // 7. Re-scoring with unchanged criteria changes nothing, keeps the reviews, and is recorded on the run.
        mvc.perform(post("/api/runs/" + runCode + "/rescore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(0))
                .andExpect(jsonPath("$.run.rescoredAt").exists());
        mvc.perform(get("/api/results/" + failedId))
                .andExpect(jsonPath("$.verdict").value("fail"))
                .andExpect(jsonPath("$.review.verdict").value("fail"));
    }

    private TestRun waitForFinish(String code) {
        await().atMost(Duration.ofSeconds(20)).until(() -> runs.findByCode(code)
                .map(r -> r.getStatus() != RunStatus.RUNNING).orElse(false));
        return runs.findByCode(code).orElseThrow();
    }
}
