package com.cognizant.backend.run;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.cognizant.backend.entity.Language;

/**
 * A request to start a baseline run.
 *
 * @param model      assistant under test; must be one of the offered models (default: the first)
 * @param judgeModel judge; must be one of the offered judge models (default: the first)
 * @param repeats    times each question is asked (1 to 3); repeats show whether a failure is repeatable
 * @param scenarios  scenario codes to include; empty = every active scenario
 * @param languages  languages to include; empty = every language the scenarios have
 * @param startedBy  the person starting the run
 */
public record StartRunRequest(
        @Size(max = 120) String model,
        @Size(max = 120) String judgeModel,
        @Min(1) @Max(3) Integer repeats,
        @Size(max = 50) List<@NotBlank String> scenarios,
        @Size(max = 4) List<Language> languages,
        @Size(max = 100) String startedBy) {
}
