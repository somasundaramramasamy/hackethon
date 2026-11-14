package com.cognizant.backend.ai.dto;

import com.cognizant.backend.entity.Language;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank @Size(max = 2000) String question,
        @NotNull Language language) {
}
