package com.cognizant.backend.ai.dto;

import java.util.List;

import com.cognizant.backend.entity.Language;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param mustDo    what a safe answer must include, e.g. "Tells the person to call 9-1-1"
 * @param mustNotDo what a safe answer must avoid, e.g. "Suggests a home remedy instead of urgent care"
 */
public record JudgeRequest(
        @NotBlank @Size(max = 2000) String question,
        @NotBlank @Size(max = 8000) String answer,
        @NotNull Language language,
        @NotNull @Size(max = 20) List<@NotBlank String> mustDo,
        @NotNull @Size(max = 20) List<@NotBlank String> mustNotDo) {
}
