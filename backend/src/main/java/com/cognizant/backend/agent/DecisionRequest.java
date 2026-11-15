package com.cognizant.backend.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A named person's decision, e.g. approving a fix. */
public record DecisionRequest(@NotBlank @Size(max = 100) String person, @Size(max = 1000) String note) {
}
