package com.cognizant.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.ai.config.AiProperties;
import com.cognizant.backend.ai.dto.AiStatus;
import com.cognizant.backend.ai.dto.AskRequest;
import com.cognizant.backend.ai.dto.AssistantAnswer;
import com.cognizant.backend.ai.dto.InvestigationReport;
import com.cognizant.backend.ai.dto.InvestigationRequest;
import com.cognizant.backend.ai.dto.JudgeRequest;
import com.cognizant.backend.ai.dto.JudgeVerdict;
import com.cognizant.backend.ai.dto.MultilingualAnswer;
import com.cognizant.backend.ai.dto.MultilingualAskRequest;

import jakarta.validation.Valid;

/** Endpoints for trying each AI role on its own. Every call except /status is a paid model call. */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final HealthAssistantService assistant;
    private final JudgeService judge;
    private final InvestigatorAgent investigator;
    private final AiAvailability availability;
    private final AiProperties props;
    private final int assistantDailyLimit;

    public AiController(HealthAssistantService assistant, JudgeService judge, InvestigatorAgent investigator,
            AiAvailability availability, AiProperties props,
            @Value("${kavasam.ai.assistant-daily-limit:0}") int assistantDailyLimit) {
        this.assistant = assistant;
        this.judge = judge;
        this.investigator = investigator;
        this.availability = availability;
        this.props = props;
        this.assistantDailyLimit = assistantDailyLimit;
    }

    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(
                new AiStatus.Provider("gemini", props.assistantModel(), availability.isGeminiConfigured()),
                new AiStatus.Provider("anthropic", props.judgeModel(), availability.isClaudeConfigured()),
                new AiStatus.Provider("anthropic", props.judgeModel(), availability.isClaudeConfigured()),
                assistantDailyLimit);
    }

    @PostMapping("/ask")
    public AssistantAnswer ask(@Valid @RequestBody AskRequest request) {
        return assistant.ask(request.question(), request.language());
    }

    /** Same question in EN, TA and HI: one Gemini call per language. */
    @PostMapping("/ask/multilingual")
    public MultilingualAnswer askMultilingual(@Valid @RequestBody MultilingualAskRequest request) {
        return assistant.askInEachLanguage(request.questions());
    }

    @PostMapping("/judge")
    public JudgeVerdict judge(@Valid @RequestBody JudgeRequest request) {
        return judge.evaluate(request);
    }

    @PostMapping("/investigate")
    public InvestigationReport investigate(@Valid @RequestBody InvestigationRequest request) {
        return investigator.investigate(request);
    }
}
