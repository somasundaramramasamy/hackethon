package com.cognizant.backend.ai.dto;

import java.util.Map;

import com.cognizant.backend.entity.Language;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** The same question written in each language, e.g. {"EN": "...", "TA": "...", "HI": "..."}. */
public record MultilingualAskRequest(
        @NotEmpty Map<Language, @NotBlank @Size(max = 2000) String> questions) {
}
