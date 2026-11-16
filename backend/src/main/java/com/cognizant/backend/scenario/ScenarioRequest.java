package com.cognizant.backend.scenario;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.RuleSignal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A scenario as entered in the form, and the format of the export/import JSON file.
 *
 * @param code   only used by import; new scenarios get the next free code (SC-07, SC-08, ...)
 * @param active only used by import; null means active
 * @param shouldDo    good practice; missing it is shown but does not fail an answer on its own
 * @param ruleSignals what the rule check requires in every answer, e.g. EMERGENCY_NUMBER; empty = none
 * @param questions   the question per language; English is required, other languages are optional
 */
public record ScenarioRequest(
        @Pattern(regexp = "SC-\\d{2,4}", message = "must look like SC-07") String code,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 80) String category,
        @NotNull @Pattern(regexp = "(?i)urgent|control", message = "must be urgent or control") String kind,
        @NotBlank @Size(max = 500) String expected,
        @Size(max = 10) List<@Size(max = 300) String> mustDo,
        @Size(max = 10) List<@Size(max = 300) String> mustNotDo,
        @Size(max = 10) List<@Size(max = 300) String> shouldDo,
        @Size(max = 120) String sourceName,
        @Size(max = 500) @Pattern(regexp = "(https?://\\S+)?", message = "must start with http:// or https://")
        String sourceUrl,
        Boolean active,
        @Size(max = 5) List<@NotNull RuleSignal> ruleSignals,
        @NotNull @Size(min = 1, message = "at least the English question is required")
        Map<Language, @NotNull @Valid QuestionInput> questions) {

    @JsonIgnore
    @AssertTrue(message = "the English question is required")
    public boolean isEnglishPresent() {
        return questions == null || questions.containsKey(Language.EN);
    }

    /** @param reviewedBy the native speaker who checked this question; blank if not reviewed yet */
    public record QuestionInput(
            @NotBlank @Size(max = 2000) String text,
            @Size(max = 100) String reviewedBy) {
    }
}
