package com.cognizant.backend.agent;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.entity.AssistantPrompt;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.prompt.PromptService;
import com.cognizant.backend.prompt.PromptView;
import com.cognizant.backend.repository.TestRunRepository;

import jakarta.validation.Valid;

/** The investigator agent, the fix agent, and the people who approve what they propose. */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final InvestigationService investigations;
    private final FixService fixes;
    private final PromptService prompts;
    private final TestRunRepository runs;

    public AgentController(InvestigationService investigations, FixService fixes, PromptService prompts,
            TestRunRepository runs) {
        this.investigations = investigations;
        this.fixes = fixes;
        this.prompts = prompts;
        this.runs = runs;
    }

    public record StartedBy(String person) {
    }

    @PostMapping("/results/{id}/investigations")
    public ResponseEntity<InvestigationService.View> investigate(@PathVariable Long id,
            @RequestBody(required = false) StartedBy body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(investigations.start(id, body == null ? null : body.person()));
    }

    @GetMapping("/investigations/{id}")
    public InvestigationService.View investigation(@PathVariable Long id) {
        return investigations.get(id);
    }

    @PostMapping("/runs/{code}/fix-proposals")
    public ResponseEntity<PromptView> proposeFix(@PathVariable String code, @RequestBody(required = false) StartedBy body) {
        AssistantPrompt proposed = fixes.propose(code, body == null ? null : body.person());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(proposed));
    }

    @GetMapping("/prompts")
    @Transactional(readOnly = true)
    public List<PromptView> prompts() {
        return prompts.all().stream().map(this::view).toList();
    }

    @GetMapping("/prompts/{version}")
    @Transactional(readOnly = true)
    public PromptView prompt(@PathVariable int version) {
        return view(prompts.get(version));
    }

    @PostMapping("/prompts/{version}/approve")
    public PromptView approve(@PathVariable int version, @Valid @RequestBody DecisionRequest body) {
        return view(fixes.approve(version, body.person(), body.note()));
    }

    @PostMapping("/prompts/{version}/reject")
    public PromptView reject(@PathVariable int version, @Valid @RequestBody DecisionRequest body) {
        return view(fixes.reject(version, body.person(), body.note()));
    }

    @PostMapping("/prompts/{version}/retest")
    public PromptView retest(@PathVariable int version, @Valid @RequestBody DecisionRequest body) {
        fixes.retest(version, body.person());
        return view(prompts.get(version));
    }

    @PostMapping("/prompts/{version}/adopt")
    public PromptView adopt(@PathVariable int version, @Valid @RequestBody DecisionRequest body) {
        return view(fixes.adopt(version, body.person()));
    }

    private PromptView view(AssistantPrompt p) {
        String source = p.getSourceRunId() == null ? null
                : runs.findById(p.getSourceRunId()).map(TestRun::getCode).orElse(null);
        List<String> retests = runs.findByTypeAndPromptVersionOrderByStartedAtDesc(RunType.RETEST, p.getVersion())
                .stream().map(TestRun::getCode).toList();
        return PromptView.of(p, source, retests);
    }
}
