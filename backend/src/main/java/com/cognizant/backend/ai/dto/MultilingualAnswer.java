package com.cognizant.backend.ai.dto;

import java.util.List;

/** The assistant's answers to the same question in each language, in EN, TA, HI order. */
public record MultilingualAnswer(String model, List<AssistantAnswer> answers) {
}
