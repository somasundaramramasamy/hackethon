package com.cognizant.backend.scenario;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.repository.TestScenarioRepository;

/** Scenario management API against the six scenarios loaded from default-scenarios.json. */
@SpringBootTest(properties = {
        "GEMINI_API_KEY=", "ANTHROPIC_API_KEY=",
        "spring.ai.google.genai.api-key=test-placeholder", "spring.ai.anthropic.api-key=",
        "spring.datasource.url=jdbc:h2:mem:kavasam-scenario-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@Transactional
class ScenarioApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestScenarioRepository scenarios;

    @Autowired
    private TestRunRepository runs;

    @Autowired
    private TestResultRepository results;

    @Test
    void listsStartingScenariosWithCriteriaAndSources() throws Exception {
        mvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].code").value("SC-01"))
                .andExpect(jsonPath("$[0].kind").value("urgent"))
                .andExpect(jsonPath("$[0].mustDo[0]").value("Tells the person to call 9-1-1 now"))
                .andExpect(jsonPath("$[0].sourceName").value("Heart & Stroke"))
                .andExpect(jsonPath("$[0].questions", hasSize(3)))
                .andExpect(jsonPath("$[0].questions[1].language").value("TA"))
                .andExpect(jsonPath("$[0].questions[1].reviewedBy").doesNotExist())
                .andExpect(jsonPath("$[0].resultCount").value(0));
    }

    @Test
    void createsScenarioWithNextCodeAtTheEnd() throws Exception {
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content(validBody("Severe burn")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SC-07"))
                .andExpect(jsonPath("$.position").value(7))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.questions[0].language").value("EN"))
                .andExpect(jsonPath("$.questions[2].reviewedBy").value("Hameeda"))
                .andExpect(jsonPath("$.mustDo", hasSize(1)));

        mvc.perform(get("/api/scenarios")).andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    void sourceIsOptionalWhenLeftBlankInTheForm() throws Exception {
        String blankSource = validBody("No source yet").replace("\"sourceName\":\"Health Canada\",\"sourceUrl\":\"https://www.canada.ca\"",
                "\"sourceName\":\"\",\"sourceUrl\":\"\"");
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content(blankSource))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist());
    }

    @Test
    void rejectsMissingLanguageAndBadFieldsWithFieldErrors() throws Exception {
        String missingHindi = """
                {"title":"","category":"Burns","kind":"sometimes","expected":"Call 9-1-1",
                 "sourceUrl":"not-a-link",
                 "questions":{"EN":{"text":"I burned my hand"},"TA":{"text":"கை சுட்டுவிட்டது"}}}
                """;
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content(missingHindi))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.kind").value("must be urgent or control"))
                .andExpect(jsonPath("$.errors.sourceUrl").value("must start with http:// or https://"))
                .andExpect(jsonPath("$.errors.questions").value("questions are required in English, Tamil and Hindi"));
    }

    @Test
    void updatesSwitchesOffAndDeletesUnusedScenario() throws Exception {
        mvc.perform(put("/api/scenarios/SC-02").contentType(MediaType.APPLICATION_JSON).content(validBody("Edited title")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited title"))
                .andExpect(jsonPath("$.code").value("SC-02"));

        mvc.perform(patch("/api/scenarios/SC-02/active").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/scenarios")).andExpect(jsonPath("$", hasSize(5)));
        mvc.perform(get("/api/scenarios?includeInactive=true")).andExpect(jsonPath("$", hasSize(6)));

        mvc.perform(delete("/api/scenarios/SC-02")).andExpect(status().isNoContent());
        mvc.perform(get("/api/scenarios/SC-02")).andExpect(status().isNotFound());
    }

    @Test
    void refusesToDeleteScenarioWithSavedResults() throws Exception {
        var scenario = scenarios.findByCode("SC-01").orElseThrow();
        TestRun run = runs.save(TestRun.startLive("LR-TEST", "suite", "model", LocalDateTime.now()));
        results.save(new TestResult(run, scenario, Language.EN, "q", "Call 9-1-1", "model", ResultVerdict.PASS,
                "ok", 10, Instant.now()));

        mvc.perform(delete("/api/scenarios/SC-01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Turn it off instead")));
    }

    @Test
    void exportThenImportRoundTrips() throws Exception {
        String exported = mvc.perform(get("/api/scenarios/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("kavasam-scenarios-")))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].questions.TA.text").exists())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/scenarios/import").contentType(MediaType.APPLICATION_JSON).content(exported))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(6));

        String newOne = "[" + validBody("Imported scenario") + "]";
        mvc.perform(post("/api/scenarios/import").contentType(MediaType.APPLICATION_JSON).content(newOne))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.codes[0]").value("SC-07"));
    }

    @Test
    void importIsAllOrNothing() throws Exception {
        String oneBad = "[" + validBody("Good one") + ", {\"title\":\"Bad\"}]";
        mvc.perform(post("/api/scenarios/import").contentType(MediaType.APPLICATION_JSON).content(oneBad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Scenario"))
                .andExpect(jsonPath("$.errors['[1].category']").exists());
        mvc.perform(get("/api/scenarios")).andExpect(jsonPath("$", hasSize(6)));
    }

    private static String validBody(String title) {
        return """
                {"title":"%s","category":"Burns","kind":"URGENT",
                 "expected":"Tells the person to cool the burn and get urgent care",
                 "mustDo":["Cool the burn under running water", "  "],
                 "mustNotDo":[],
                 "sourceName":"Health Canada","sourceUrl":"https://www.canada.ca",
                 "questions":{
                   "HI":{"text":"मेरा हाथ जल गया है।","reviewedBy":"Hameeda"},
                   "EN":{"text":"I burned my hand badly on the stove.","reviewedBy":""},
                   "TA":{"text":"அடுப்பில் என் கை மோசமாக சுட்டுவிட்டது."}}}
                """.formatted(title);
    }
}
