package com.cognizant.backend.llm;

/**
 * A model people can pick in the app.
 *
 * @param id    the provider's model id, e.g. meta-llama/llama-3.2-3b-instruct
 * @param label what the app shows, e.g. "Llama 3.2 3B (Meta)"
 * @param note  one line on why it is offered
 */
public record ModelOption(String id, String label, String note) {
}
