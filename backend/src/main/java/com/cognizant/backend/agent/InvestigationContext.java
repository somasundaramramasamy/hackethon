package com.cognizant.backend.agent;

import java.util.List;

import com.cognizant.backend.entity.Language;

/** What the investigator knows when it starts, detached from JPA for the background thread. */
record InvestigationContext(
        Long investigationId,
        String assistantModel,
        String agentModel,
        String systemPrompt,
        String scenarioTitle,
        String expectedBehaviour,
        List<String> mustDo,
        List<String> mustNotDo,
        Language language,
        String question,
        String answer,
        String translation,
        String verdictDetail,
        String judgeReason,
        String englishQuestion,
        String englishAnswer) {
}
