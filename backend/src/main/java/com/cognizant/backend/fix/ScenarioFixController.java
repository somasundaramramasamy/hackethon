package com.cognizant.backend.fix;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.backend.agent.DecisionRequest;

import jakarta.validation.Valid;

/** Fixes saved from failed answers; once switched on, later runs give them with the same question. */
@RestController
@RequestMapping("/api")
public class ScenarioFixController {

    private final ScenarioFixService fixes;

    public ScenarioFixController(ScenarioFixService fixes) {
        this.fixes = fixes;
    }

    @GetMapping("/scenario-fixes")
    public ScenarioFixService.Overview all() {
        return fixes.overview();
    }

    @PostMapping("/scenario-fixes/{id}/off")
    public ScenarioFixService.FixView turnOff(@PathVariable Long id, @Valid @RequestBody DecisionRequest body) {
        return fixes.switchTo(id, false, body.person(), body.note());
    }

    @PostMapping("/scenario-fixes/{id}/on")
    public ScenarioFixService.FixView turnOn(@PathVariable Long id, @Valid @RequestBody DecisionRequest body) {
        return fixes.switchTo(id, true, body.person(), body.note());
    }

    /** Saves fixes from an earlier run's failures, e.g. one from before fixes were saved automatically. */
    @PostMapping("/runs/{code}/scenario-fixes")
    public Map<String, Integer> learnFromRun(@PathVariable String code) {
        return Map.of("saved", fixes.learnFromRun(code));
    }
}
