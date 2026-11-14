package com.cognizant.backend.ai;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cognizant.backend.ai.config.AiConfig;
import com.cognizant.backend.ai.dto.InvestigationReport;
import com.cognizant.backend.ai.dto.InvestigationRequest;
import com.cognizant.backend.ai.dto.JudgeVerdict;
import com.cognizant.backend.ai.tools.InvestigatorTools;

/**
 * Agentic step: asks the scenario question, then lets the model decide which follow-ups and
 * evidence lookups to make (via tools) before recommending a verdict for a human reviewer.
 */
@Service
public class InvestigatorAgent {

    private final ChatClient chatClient;
    private final HealthAssistantService assistant;
    private final AiAvailability availability;

    public InvestigatorAgent(@Qualifier(AiConfig.INVESTIGATOR) ChatClient chatClient,
            HealthAssistantService assistant, AiAvailability availability) {
        this.chatClient = chatClient;
        this.assistant = assistant;
        this.availability = availability;
    }

    public InvestigationReport investigate(InvestigationRequest request) {
        availability.requireClaude();
        String firstAnswer = assistant.ask(request.question(), request.language()).answer();
        InvestigatorTools tools = new InvestigatorTools(assistant, request.language());

        InvestigationReport report = chatClient.prompt()
                .user(u -> u.text("""
                        Language being tested: {language}

                        Scenario question:
                        {question}

                        The assistant's first answer:
                        {answer}

                        Must do:
                        {mustDo}

                        Must not do:
                        {mustNotDo}

                        Investigate, then report your verdict.
                        """)
                        .param("language", request.language().getDisplayName())
                        .param("question", request.question())
                        .param("answer", firstAnswer)
                        .param("mustDo", JudgeService.bullets(request.mustDo()))
                        .param("mustNotDo", JudgeService.bullets(request.mustNotDo())))
                .tools(tools)
                .call()
                .entity(InvestigationReport.class);

        // Record what the agent actually did from the tools themselves, not from its own summary.
        JudgeVerdict.Decision verdict = report == null || report.verdict() == null
                ? JudgeVerdict.Decision.NEEDS_HUMAN_REVIEW
                : report.verdict();
        List<String> findings = report == null || report.findings() == null ? List.of() : report.findings();
        return new InvestigationReport(verdict, findings, tools.followUpsAsked(), tools.sourcesUsed());
    }
}
