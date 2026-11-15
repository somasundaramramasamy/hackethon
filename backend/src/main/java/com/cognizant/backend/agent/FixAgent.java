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

    public Proposal propose(String model, String currentPrompt, List<Failure> failures, List<Diagnosis> diagnoses) {
        String reply = llm.chat(LlmClient.Role.JUDGE, model,
                List.of(ChatMessage.system(systemPrompt), ChatMessage.user(brief(currentPrompt, failures, diagnoses))),
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
        String summary = JsonReplies.text(node, "summary");
        return new Proposal(prompt, summary == null ? "Revised system prompt." : summary,
                JsonReplies.text(node, "rationale"));
    }

    static String brief(String currentPrompt, List<Failure> failures, List<Diagnosis> diagnoses) {
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
        sb.append("\nPropose the revised system prompt.");
        return sb.toString();
    }
}
