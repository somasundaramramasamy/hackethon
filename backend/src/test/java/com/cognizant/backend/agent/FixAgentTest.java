package com.cognizant.backend.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class FixAgentTest {

    private static final List<FixAgent.Failure> FAILURES = List.of(new FixAgent.Failure("SC-01 Chest pain", "Tamil",
            "question", "answer", "translation", "Judge: no 9-1-1.", null));

    @Test
    void aRejectedProposalAndItsNoteReachTheNextBrief() {
        var rejected = new FixAgent.Rejection(2, "Prompt that echoes the tests", "Adds examples",
                "Yokeshwaran", "Too close to the test questions; keep it general.");
        String brief = FixAgent.brief("Current prompt", FAILURES, List.of(), List.of(rejected), List.of());
        assertThat(brief)
                .contains("A person already rejected an earlier proposal")
                .contains("Rejected v2 (Adds examples)")
                .contains("Rejected by Yokeshwaran: Too close to the test questions; keep it general.")
                .contains("Prompt that echoes the tests");
    }

    @Test
    void aFirstProposalHasNoRejectionSection() {
        assertThat(FixAgent.brief("Current prompt", FAILURES, List.of(), List.of(), List.of()))
                .doesNotContain("rejected")
                .doesNotContain("not kept");
    }

    @Test
    void aRetestedVersionThatBrokeAnswersReachesTheNextBrief() {
        var broke = new FixAgent.Failure("SC-02 Heart attack signs", "English", "question",
                "Call 9-1-1 now.", null, "Judge: does not say why it is an emergency.", null);
        var tried = new FixAgent.Tried(3, "Short emergency prompt", "Emergencies first", "RT-1", 10, 7,
                List.of(broke));
        String brief = FixAgent.brief("Current prompt", FAILURES, List.of(), List.of(), List.of(tried));
        assertThat(brief)
                .contains("approved and retested, but not kept")
                .contains("Retested v3 (Emergencies first)")
                .contains("Retest RT-1: 10 fixed, 7 still failing, 1 new failures.")
                .contains("SC-02 Heart attack signs — English: Judge: does not say why it is an emergency.")
                .contains("Answer under v3 (start): Call 9-1-1 now.")
                .contains("Short emergency prompt");
    }
}
