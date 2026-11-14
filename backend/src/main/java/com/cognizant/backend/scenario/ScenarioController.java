package com.cognizant.backend.scenario;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioService service;

    public ScenarioController(ScenarioService service) {
        this.service = service;
    }

    /** Active scenarios (what the next live run asks), or all with {@code ?includeInactive=true}. */
    @GetMapping
    public List<ScenarioDetail> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @GetMapping("/export")
    public ResponseEntity<List<ScenarioRequest>> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"kavasam-scenarios-" + LocalDate.now() + ".json\"")
                .body(service.export());
    }

    @PostMapping("/import")
    public ImportResult importAll(@RequestBody List<ScenarioRequest> scenarios) {
        return service.importAll(scenarios);
    }

    @GetMapping("/{code}")
    public ScenarioDetail get(@PathVariable String code) {
        return service.get(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioDetail create(@Valid @RequestBody ScenarioRequest request) {
        return service.create(request);
    }

    @PutMapping("/{code}")
    public ScenarioDetail update(@PathVariable String code, @Valid @RequestBody ScenarioRequest request) {
        return service.update(code, request);
    }

    /** Body: {"active": true|false} */
    @PatchMapping("/{code}/active")
    public ScenarioDetail setActive(@PathVariable String code, @RequestBody Map<String, Boolean> body) {
        return service.setActive(code, Boolean.TRUE.equals(body.get("active")));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        service.delete(code);
    }
}
