package com.cognizant.backend.settings;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.RuleSignal;
import com.cognizant.backend.llm.LlmProperties;
import com.cognizant.backend.llm.ModelOption;

/** What the frontend needs to build its forms: models on offer, languages, signals, and the app mode. */
@RestController
@RequestMapping("/api")
public class SettingsController {

    private final LlmProperties llm;
    private final AppProperties app;

    public SettingsController(LlmProperties llm, AppProperties app) {
        this.llm = llm;
        this.app = app;
    }

    public record Option(String value, String label) {
    }

    public record Settings(List<ModelOption> assistantModels, List<ModelOption> judgeModels,
            boolean assistantConfigured, boolean judgeConfigured, boolean readOnly, List<Option> languages,
            List<Option> ruleSignals) {
    }

    @GetMapping("/settings")
    public Settings settings() {
        return new Settings(llm.assistantModels(), llm.judgeModels(), llm.isAssistantConfigured(),
                llm.isJudgeConfigured(), app.isReadOnly(),
                java.util.Arrays.stream(Language.values()).map(l -> new Option(l.name(), l.getDisplayName())).toList(),
                java.util.Arrays.stream(RuleSignal.values()).map(s -> new Option(s.name(), s.getLabel())).toList());
    }
}
