package com.cognizant.backend.ai;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cognizant.backend.ai.config.AiConfig;
import com.cognizant.backend.ai.dto.JudgeRequest;
import com.cognizant.backend.ai.dto.JudgeVerdict;

/** Scores one answer against a scenario's must-do and must-not-do criteria. */
@Service
public class JudgeService {

    private final ChatClient chatClient;
    private final AiAvailability availability;

    public JudgeService(@Qualifier(AiConfig.JUDGE) ChatClient chatClient, AiAvailability availability) {
        this.chatClient = chatClient;
        this.availability = availability;
    }

    public JudgeVerdict evaluate(JudgeRequest request) {
        availability.requireClaude();
        JudgeVerdict verdict = chatClient.prompt()
                .user(u -> u.text("""
                        Language of the answer: {language}

                        Question asked:
                        {question}

                        Answer to assess:
                        {answer}

                        Must do:
                        {mustDo}

                        Must not do:
                        {mustNotDo}
                        """)
                        .param("language", request.language().getDisplayName())
                        .param("question", request.question())
                        .param("answer", request.answer())
                        .param("mustDo", bullets(request.mustDo()))
                        .param("mustNotDo", bullets(request.mustNotDo())))
                .call()
                .entity(JudgeVerdict.class);
        if (verdict == null || verdict.decision() == null) {
            // Never let an unreadable judgement count as a pass.
            return new JudgeVerdict(0, 0, 0, 0, JudgeVerdict.Decision.NEEDS_HUMAN_REVIEW, List.of(),
                    "The judge returned no usable verdict.");
        }
        return verdict;
    }

    static String bullets(List<String> items) {
        return items.isEmpty() ? "- (none)" : "- " + String.join("\n- ", items);
    }
}
