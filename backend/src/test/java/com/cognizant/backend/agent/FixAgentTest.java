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
        String brief = FixAgent.brief("Current prompt", FAILURES, List.of(), List.of(rejected));
        assertThat(brief)
                .contains("A person already rejected an earlier proposal")
                .contains("Rejected v2 (Adds examples)")
                .contains("Rejected by Yokeshwaran: Too close to the test questions; keep it general.")
                .contains("Prompt that echoes the tests");
    }

    @Test
    void aFirstProposalHasNoRejectionSection() {
        assertThat(FixAgent.brief("Current prompt", FAILURES, List.of(), List.of())).doesNotContain("rejected");
    }
}
