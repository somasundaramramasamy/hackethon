package com.cognizant.backend.run;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Guards that protect money and data: no key means no run, and read-only demo mode refuses changes. */
class ApiGuardsTest {

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:kavasam-nokey-test;DB_CLOSE_DELAY=-1;MODE=MySQL")
    @AutoConfigureMockMvc
    class WithoutAKey {

        @Autowired
        private MockMvc mvc;

        @Test
        void refusesToStartARunButStillShowsTheApp() throws Exception {
            mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.title").value("Model Not Configured"));
            mvc.perform(get("/api/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assistantConfigured").value(false))
                    .andExpect(jsonPath("$.readOnly").value(false))
                    .andExpect(jsonPath("$.languages[3].value").value("ML"));
            mvc.perform(get("/api/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activePromptVersion").value(1))
                    .andExpect(jsonPath("$.activeScenarios").value(15));
        }

        @Test
        void validatesTheRunRequestBeforeAnythingElse() throws Exception {
            mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("{\"repeats\":9}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.repeats").exists());
        }
    }

    @Nested
    @SpringBootTest(properties = {"kavasam.read-only=true",
            "spring.datasource.url=jdbc:h2:mem:kavasam-readonly-test;DB_CLOSE_DELAY=-1;MODE=MySQL"})
    @AutoConfigureMockMvc
    class InReadOnlyMode {

        @Autowired
        private MockMvc mvc;

        @Test
        void refusesEveryChangeButAllowsBrowsing() throws Exception {
            mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Read-Only Demo"));
            mvc.perform(post("/api/prompts/1/adopt").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/scenarios")).andExpect(status().isOk());
            mvc.perform(get("/api/settings")).andExpect(jsonPath("$.readOnly").value(true));
        }
    }
}
