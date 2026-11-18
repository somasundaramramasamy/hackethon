package com.cognizant.backend.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.llm.ModelOption;

class RunNameTest {

    private static final List<ModelOption> MODELS = List.of(
            new ModelOption("google/gemma-4-31b-it", "Gemma 4 31B (Google, open)", "note"));

    @Test
    void namesAnUnnamedRunFromItsModelAndSettings() {
        assertThat(RunService.defaultName(MODELS, "google/gemma-4-31b-it", 4, 3)).isEqualTo("Gemma 4 31B · 4 languages · ×3");
        assertThat(RunService.defaultName(MODELS, "google/gemma-4-31b-it", 1, 1)).isEqualTo("Gemma 4 31B · 1 language");
        assertThat(RunService.defaultName(MODELS, "other/model", 2, 1)).isEqualTo("other/model · 2 languages");
    }

    @Test
    void olderRunsWithoutANameStillShowOne() {
        TestRun baseline = new TestRun("RUN-1", "suite", RunType.BASELINE, "meta-llama/llama-3.2-3b-instruct", "judge",
                1, null, 1, 1, null, java.time.LocalDateTime.now());
        assertThat(baseline.getDisplayName()).isEqualTo("Test of llama-3.2-3b-instruct");
        TestRun retest = new TestRun("RT-1", "suite", RunType.RETEST, "m", "judge", 3, 1L, 1, 1, null,
                java.time.LocalDateTime.now());
        assertThat(retest.getDisplayName()).isEqualTo("Retest of fix v3");
        retest.rename("  ");
        assertThat(retest.getName()).isNull();
        retest.rename(" Fix check ");
        assertThat(retest.getDisplayName()).isEqualTo("Fix check");
    }
}
