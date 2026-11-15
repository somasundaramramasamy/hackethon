package com.cognizant.backend.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.entity.AssistantPrompt;
import com.cognizant.backend.entity.PromptStatus;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.repository.AssistantPromptRepository;

/** System prompt versions of the assistant under test, and the rules for moving between them. */
@Service
@Transactional(readOnly = true)
public class PromptService {

    private final AssistantPromptRepository prompts;
    private final Resource initialPrompt;

    public PromptService(AssistantPromptRepository prompts,
            @Value("classpath:prompts/health-assistant-system.txt") Resource initialPrompt) {
        this.prompts = prompts;
        this.initialPrompt = initialPrompt;
    }

    /** Creates version 1 from the bundled prompt if the database has no versions yet. */
    @Transactional
    public boolean seedIfEmpty() {
        if (prompts.count() > 0) {
            return false;
        }
        try {
            prompts.save(AssistantPrompt.initial(initialPrompt.getContentAsString(StandardCharsets.UTF_8).strip(),
                    Instant.now()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    public AssistantPrompt active() {
        return prompts.findFirstByStatus(PromptStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active system prompt version"));
    }

    public AssistantPrompt get(int version) {
        return prompts.findByVersion(version)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt version " + version + " not found"));
    }

    public List<AssistantPrompt> all() {
        return prompts.findAllByOrderByVersionDesc();
    }

    @Transactional
    public AssistantPrompt propose(String text, String summary, String rationale, int basedOnVersion, Long sourceRunId,
            String createdBy) {
        int version = prompts.findMaxVersion() + 1;
        return prompts.save(AssistantPrompt.proposal(version, text, summary, rationale, basedOnVersion, sourceRunId,
                createdBy, Instant.now()));
    }

    @Transactional
    public AssistantPrompt approve(int version, String person, String note) {
        AssistantPrompt p = get(version);
        p.approve(person, note, Instant.now());
        return p;
    }

    @Transactional
    public AssistantPrompt reject(int version, String person, String note) {
        AssistantPrompt p = get(version);
        p.reject(person, note, Instant.now());
        return p;
    }

    /** Puts a version in use and retires the current one. Adopting a retired version is a rollback. */
    @Transactional
    public AssistantPrompt adopt(int version, String person) {
        AssistantPrompt next = get(version);
        if (next.getStatus() == PromptStatus.ACTIVE) {
            return next;
        }
        prompts.findFirstByStatus(PromptStatus.ACTIVE).ifPresent(AssistantPrompt::retire);
        prompts.flush();
        next.adopt(person, Instant.now());
        return next;
    }
}
