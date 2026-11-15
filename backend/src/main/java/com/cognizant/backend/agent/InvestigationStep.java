package com.cognizant.backend.agent;

/**
 * One entry in the agent's decision log.
 *
 * @param action  follow_up, rephrase, ask_in_english or conclude
 * @param message what the agent sent to the assistant (null for conclude)
 * @param reply   the assistant's reply to it (null for conclude, or if the call failed)
 * @param replySummary the agent's English summary of the reply, written at its next step
 */
public record InvestigationStep(int step, String thought, String action, String message, String reply,
        String replySummary) {

    InvestigationStep withReplySummary(String summary) {
        return new InvestigationStep(step, thought, action, message, reply, summary);
    }
}
