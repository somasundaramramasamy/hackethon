package com.cognizant.backend.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.cognizant.backend.llm.ChatMessage;
import com.cognizant.backend.llm.JsonReplies;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.llm.LlmException;
import com.cognizant.backend.llm.LlmProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Proposes a revised system prompt from a run's failures and the investigator's diagnoses. */
@Component
public class FixAgent {

    /** Longest prompt the agent may propose; longer prompts tend to confuse small models. */
    static final int MAX_PROMPT_CHARS = 6000;

    public record Failure(String scenario, String language, String question, String answerExcerpt,
            String translationExcerpt, String whyFlagged, String humanNote) {
    }

    public record Diagnosis(String scenario, String language, String conclusion, String failureType, String diagnosis,
            String suggestedFix) {
    }

    /** An earlier proposal for the same failures that a person rejected, with their reason. */
    public record Rejection(int version, String prompt, String summary, String rejectedBy, String note) {
    }

    /**
     * An earlier proposal for the same failures that a person approved and retested, but that was not kept: what
     * its retest fixed, and the answers it newly broke.
     */
    public record Tried(int version, String prompt, String summary, String retestCode, int fixed, int stillFailing,
            List<Failure> broke) {
    }

    public record Proposal(String prompt, String summary, String rationale) {
    }

    private final LlmClient llm;
    private final LlmProperties props;
    private final JsonMapper json;
    private final String systemPrompt;

    public FixAgent(LlmClient llm, LlmProperties props, JsonMapper json,
            @Value("classpath:prompts/fix-system.txt") Resource systemPrompt) {
        this.llm = llm;
        this.props = props;
        this.json = json;
        try {
            this.systemPrompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Proposal propose(String model, String currentPrompt, List<Failure> failures, List<Diagnosis> diagnoses,
            List<Rejection> rejections, List<Tried> tried) {
        String reply = llm.chat(LlmClient.Role.JUDGE, model,
                List.of(ChatMessage.system(systemPrompt),
                        ChatMessage.user(brief(currentPrompt, failures, diagnoses, rejections, tried))),
                props.judgeMaxTokens()).content();
        JsonNode node = JsonReplies.parseObject(json, reply);
        String prompt = JsonReplies.text(node, "prompt");
        if (prompt == null) {
            throw new LlmException(502, "The fix agent returned no prompt");
        }
        if (prompt.length() > MAX_PROMPT_CHARS) {
            throw new LlmException(502, "The fix agent's prompt is too long (" + prompt.length() + " characters)");
        }
        if (prompt.strip().equals(currentPrompt.strip())) {
            throw new LlmException(502, "The fix agent proposed no change");
        }
        if (rejections.stream().anyMatch(r -> r.prompt().strip().equals(prompt.strip()))) {
            throw new LlmException(502, "The fix agent proposed a version that was already rejected");
        }
        if (tried.stream().anyMatch(t -> t.prompt().strip().equals(prompt.strip()))) {
            throw new LlmException(502, "The fix agent proposed a version that was already retested and not kept");
        }
        String summary = JsonReplies.text(node, "summary");
        return new Proposal(prompt, summary == null ? "Revised system prompt." : summary,
                JsonReplies.text(node, "rationale"));
    }

    static String brief(String currentPrompt, List<Failure> failures, List<Diagnosis> diagnoses,
            List<Rejection> rejections, List<Tried> tried) {
        StringBuilder sb = new StringBuilder("Current system prompt:\n<<<\n").append(currentPrompt).append("\n>>>\n");
        sb.append("\nFailures in the last test run (").append(failures.size()).append("):\n");
        int n = 1;
        for (Failure f : failures) {
            sb.append("\n").append(n++).append(". ").append(f.scenario()).append(" — ").append(f.language()).append('\n');
            sb.append("Question: ").append(f.question()).append('\n');
            sb.append("Answer (start): ").append(f.answerExcerpt()).append('\n');
            if (f.translationExcerpt() != null) {
                sb.append("Answer in English (start): ").append(f.translationExcerpt()).append('\n');
            }
            sb.append("Why flagged: ").append(f.whyFlagged()).append('\n');
            if (f.humanNote() != null) {
                sb.append("Reviewer's note: ").append(f.humanNote()).append('\n');
            }
        }
        if (!diagnoses.isEmpty()) {
            sb.append("\nThe investigator agent's findings:\n");
            for (Diagnosis d : diagnoses) {
                sb.append("- ").append(d.scenario()).append(" — ").append(d.language()).append(": ")
                        .append(d.conclusion()).append(", ").append(d.failureType()).append(". ")
                        .append(d.diagnosis() == null ? "" : d.diagnosis());
                if (d.suggestedFix() != null) {
                    sb.append(" Suggested fix: ").append(d.suggestedFix());
                }
                sb.append('\n');
            }
        }
        if (!rejections.isEmpty()) {
            sb.append("\nA person already rejected ").append(rejections.size() == 1 ? "an earlier proposal" : "earlier proposals")
                    .append(" for these failures. Do what their note asks; do not repeat what they rejected.\n");
            for (Rejection r : rejections) {
                sb.append("\nRejected v").append(r.version()).append(" (").append(r.summary()).append(")\n");
                sb.append("Rejected by ").append(r.rejectedBy()).append(": ")
                        .append(r.note() == null ? "no reason given" : r.note()).append('\n');
                sb.append("Its prompt:\n<<<\n").append(r.prompt()).append("\n>>>\n");
            }
        }
        if (!tried.isEmpty()) {
            sb.append("\nEarlier proposals for these failures were approved and retested, but not kept, because the")
                    .append(" retest showed they broke answers that used to pass. Keep what they fixed, and avoid")
                    .append(" what broke.\n");
            for (Tried t : tried) {
                sb.append("\nRetested v").append(t.version()).append(" (").append(t.summary()).append(")\n");
                sb.append("Retest ").append(t.retestCode()).append(": ").append(t.fixed()).append(" fixed, ")
                        .append(t.stillFailing()).append(" still failing, ").append(t.broke().size())
                        .append(" new failures.\n");
                if (!t.broke().isEmpty()) {
                    sb.append("Answers it broke, which passed with the current prompt:\n");
                    for (Failure f : t.broke()) {
                        sb.append("- ").append(f.scenario()).append(" — ").append(f.language()).append(": ")
                                .append(f.whyFlagged()).append('\n');
                        sb.append("  Answer under v").append(t.version()).append(" (start): ")
                                .append(f.translationExcerpt() != null ? f.translationExcerpt() : f.answerExcerpt())
                                .append('\n');
                        if (f.humanNote() != null) {
                            sb.append("  Reviewer's note: ").append(f.humanNote()).append('\n');
                        }
                    }
                }
                sb.append("Its prompt:\n<<<\n").append(t.prompt()).append("\n>>>\n");
            }
        }
        sb.append("\nPropose the revised system prompt.");
        return sb.toString();
    }
}
