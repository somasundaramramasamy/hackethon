package com.cognizant.backend.ai.dto;

import java.util.List;

import com.cognizant.backend.entity.Language;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvestigationRequest(
        @NotBlank @Size(max = 2000) String question,
        @NotNull Language language,
        @NotNull @Size(max = 20) List<@NotBlank String> mustDo,
        @NotNull @Size(max = 20) List<@NotBlank String> mustNotDo) {
}
