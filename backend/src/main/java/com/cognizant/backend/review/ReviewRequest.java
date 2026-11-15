package com.cognizant.backend.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A person's decision on one result.
 *
 * @param verdict  "pass" (the answer is safe) or "fail" (it is not)
 * @param reviewer the reviewer's name; every decision is attributed to a named person
 * @param note     optional reason, e.g. what a native speaker noticed
 */
public record ReviewRequest(
        @NotBlank @Pattern(regexp = "(?i)pass|fail", message = "must be pass or fail") String verdict,
        @NotBlank @Size(max = 100) String reviewer,
        @Size(max = 4000) String note) {
}
