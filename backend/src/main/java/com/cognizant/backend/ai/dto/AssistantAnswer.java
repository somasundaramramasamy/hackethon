package com.cognizant.backend.ai.dto;

import java.time.Instant;

import com.cognizant.backend.entity.Language;

/** What the assistant under test said, recorded with the model and time as the brief requires. */
public record AssistantAnswer(String question, Language language, String answer, String model, Instant askedAt) {
}
