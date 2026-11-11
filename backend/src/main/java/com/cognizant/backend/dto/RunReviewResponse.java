package com.cognizant.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.cognizant.backend.entity.AssistantResponse;
import com.cognizant.backend.entity.EvaluationScore;
import com.cognizant.backend.entity.EvidenceSource;
import com.cognizant.backend.entity.Finding;
import com.cognizant.backend.entity.HumanReview;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ReviewCase;
import com.cognizant.backend.entity.RunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RunReviewResponse(
        String id,
        String title,
        String category,
        List<String> languages,
        LocalDateTime startedAt,
        String model,
        String runStatus,
        String reviewStatus,
        String verification,
        int pipelineStep,
        String prompt,
        List<Response> responses,
        int overallScore,
        List<Score> scores,
        List<FindingItem> findings,
        List<Source> sources,
        Judge judge,
        List<HumanReviewResponse> history) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(String lang, String name, String verdict, String text, String translation) {

        static Response from(AssistantResponse r) {
            return new Response(r.getLanguage().name(), r.getLanguage().getDisplayName(),
                    ApiValues.of(r.getVerdict()), r.getText(), r.getTranslation());
        }
    }

    public record Score(String key, String label, double value, String rating, String tone) {

        static Score from(EvaluationScore s) {
            return new Score(s.getMetricKey(), s.getLabel(), s.getValue(), s.getRating(), s.getTone());
        }
    }

    public record FindingItem(boolean ok, String text) {

        static FindingItem from(Finding f) {
            return new FindingItem(f.isPassed(), f.getText());
        }
    }

    public record Source(String org, @JsonProperty("short") String shortName, String title, String url) {

        static Source from(EvidenceSource s) {
            return new Source(s.getOrganization(), s.getShortName(), s.getTitle(), s.getUrl());
        }
    }

    public record Judge(String decision, String reason) {
    }

    public static RunReviewResponse from(ReviewCase c, List<HumanReview> history) {
        return new RunReviewResponse(
                c.getCode(),
                c.getTitle(),
                c.getCategory(),
                c.getLanguages().stream().map(Language::name).toList(),
                c.getStartedAt(),
                c.getModel(),
                c.getRunStatus() == RunStatus.COMPLETED ? "Completed" : "Needs Review",
                ApiValues.of(c.getReviewStatus()),
                ApiValues.of(c.getVerification()),
                c.getPipelineStep(),
                c.getPrompt(),
                c.getResponses().stream().map(Response::from).toList(),
                c.getOverallScore(),
                c.getScores().stream().map(Score::from).toList(),
                c.getFindings().stream().map(FindingItem::from).toList(),
                c.getSources().stream().map(Source::from).toList(),
                new Judge(c.getJudgeDecision(), c.getJudgeReason()),
                history.stream().map(HumanReviewResponse::from).toList());
    }
}
