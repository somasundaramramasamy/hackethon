package com.cognizant.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.TestRun;

public record RunSummaryResponse(
        String id,
        String suite,
        List<String> languages,
        int total,
        int passed,
        int failed,
        String status,
        LocalDateTime startedAt) {

    public static RunSummaryResponse from(TestRun run) {
        return new RunSummaryResponse(
                run.getCode(),
                run.getSuite(),
                run.getLanguages().stream().map(Language::name).toList(),
                run.getTotal(),
                run.getPassed(),
                run.getFailed(),
                ApiValues.of(run.getStatus()),
                run.getStartedAt());
    }
}
