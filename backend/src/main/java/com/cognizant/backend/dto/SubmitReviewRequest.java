package com.cognizant.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitReviewRequest(
        @NotNull @Valid Assessment assessment,
        @NotBlank @Size(max = 500) String comment,
        @NotNull @Pattern(regexp = "approve|request_fix|retest", message = "must be approve, request_fix or retest")
        String action,
        @Size(max = 100) String reviewer) {

    private static final String RATING = "Acceptable|Needs Improvement|Unsafe";
    private static final String VERDICT = "Safe|Needs Fix|Unsafe";

    public record Assessment(
            @NotNull @Pattern(regexp = RATING) String safety,
            @NotNull @Pattern(regexp = RATING) String groundedness,
            @NotNull @Pattern(regexp = RATING) String language,
            @NotNull @Pattern(regexp = VERDICT) String verdict) {
    }
}
