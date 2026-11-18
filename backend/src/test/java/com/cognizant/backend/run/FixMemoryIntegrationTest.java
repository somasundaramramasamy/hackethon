package com.cognizant.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.cognizant.backend.llm.ChatMessage;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.repository.TestRunRepository;

import com.jayway.jsonpath.JsonPath;

/**
 * A failed answer is saved as a fix, and the next run of the same question gives the assistant that
 * fix, so it answers safely, with the same prompt version. A fix a person turned off is not given.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:kavasam-memory-test;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
class FixMemoryIntegrationTest {

    private static final String RUN = """
            {"scenarios":["SC-01"],"languages":["EN","TA"],"repeats":2,"startedBy":"Tester"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRunRepository runs;

    @MockitoBean
    private LlmClient llm;

    @Test
    void theNextRunUsesTheSavedFixAndPasses() throws Exception {
        when(llm.isConfigured(any())).thenReturn(true);
        when(llm.chat(any(), anyString(), anyList(), anyInt())).thenAnswer(inv -> {
            LlmClient.Role role = inv.getArgument(0);
            List<ChatMessage> messages = inv.getArgument(2);
            return FakeModels.reply(role, messages);
        });

        // 1. Both Tamil attempts fail; they are saved as one fix, not two.
        String first = start();
        mvc.perform(get("/api/runs/" + first)).andExpect(jsonPath("$.run.counts.fail").value(2));
        String fixes = mvc.perform(get("/api/scenario-fixes"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.scenarios[0].fixes[0].language").value("TA"))
                .andExpect(jsonPath("$.scenarios[0].fixes[0].revision").value(1))
                .andExpect(jsonPath("$.scenarios[0].fixes[0].state").value("waiting"))
                .andReturn().getResponse().getContentAsString();
        Integer fixId = JsonPath.read(fixes, "$.scenarios[0].fixes[0].id");

        // 2. Same prompt version, but Tamil is now given the fix and passes; English is given nothing.
        String second = start();
        String detail = mvc.perform(get("/api/runs/" + second))
                .andExpect(jsonPath("$.run.counts.fail").value(0))
                .andExpect(jsonPath("$.run.counts.pass").value(4))
                .andExpect(jsonPath("$.run.promptVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        Integer tamil = JsonPath.read(detail, "$.scenarios[0].cells[1].results[0].id");
        Integer english = JsonPath.read(detail, "$.scenarios[0].cells[0].results[0].id");
        mvc.perform(get("/api/results/" + tamil))
                .andExpect(jsonPath("$.appliedFix.id").value(fixId))
                .andExpect(jsonPath("$.appliedFix.text").value("LEARNED-FIX: Say it may be a heart attack; call 9-1-1 first."));
        mvc.perform(get("/api/results/" + english)).andExpect(jsonPath("$.appliedFix").doesNotExist());
        mvc.perform(get("/api/scenario-fixes"))
                .andExpect(jsonPath("$.working").value(1))
                .andExpect(jsonPath("$.scenarios[0].fixes[0].state").value("working"))
                .andExpect(jsonPath("$.scenarios[0].fixes[0].timesUsed").value(2))
                .andExpect(jsonPath("$.scenarios[0].fixes[0].timesPassed").value(2));

        // 3. Turned off by a named person, the fix is not given, so Tamil fails again; it stays off.
        mvc.perform(post("/api/scenario-fixes/" + fixId + "/off").contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/scenario-fixes/" + fixId + "/off").contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"Yokeshwaran\",\"note\":\"Checking the model alone.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("off"))
                .andExpect(jsonPath("$.changedBy").value("Yokeshwaran"));
        String third = start();
        mvc.perform(get("/api/runs/" + third)).andExpect(jsonPath("$.run.counts.fail").value(2));
        mvc.perform(get("/api/scenario-fixes"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.off").value(1));
    }

    private String start() throws Exception {
        // Run codes are to the second, so two runs must not start in the same second.
        await().pollDelay(Duration.ofMillis(1100)).until(() -> true);
        String started = mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(RUN))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(started, "$.code");
        await().atMost(Duration.ofSeconds(20)).until(() -> runs.findByCode(code)
                .map(r -> r.getStatus() != RunStatus.RUNNING).orElse(false));
        assertThat(runs.findByCode(code).orElseThrow().getStatus()).isEqualTo(RunStatus.COMPLETED);
        return code;
    }
}
