package com.cognizant.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.cognizant.backend.ai.AiNotConfiguredException;
import com.cognizant.backend.live.RunAlreadyActiveException;
import com.cognizant.backend.scenario.InvalidScenarioException;
import com.cognizant.backend.scenario.ScenarioInUseException;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;

/**
 * Returns errors as RFC 9457 problem details. The base class already covers malformed JSON (400)
 * and unsupported methods (405).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(ScenarioInUseException.class)
    ProblemDetail handleScenarioInUse(ScenarioInUseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Scenario In Use");
        return problem;
    }

    @ExceptionHandler(InvalidScenarioException.class)
    ProblemDetail handleInvalidScenario(InvalidScenarioException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Scenario");
        problem.setProperty("errors", ex.getErrors());
        return problem;
    }

    @ExceptionHandler(RunAlreadyActiveException.class)
    ProblemDetail handleRunActive(RunAlreadyActiveException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Run In Progress");
        return problem;
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    ProblemDetail handleAiNotConfigured(AiNotConfiguredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("AI Not Configured");
        return problem;
    }

    @ExceptionHandler(RateLimitException.class)
    ProblemDetail handleRateLimit(RateLimitException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "The model provider is rate limiting requests. Try again shortly.");
        problem.setTitle("Rate Limited");
        return problem;
    }

    /** Errors returned by the Claude API (bad request, auth, overloaded, ...). */
    @ExceptionHandler(AnthropicServiceException.class)
    ProblemDetail handleModelError(AnthropicServiceException ex) {
        logger.warn("Claude API call failed with HTTP " + ex.statusCode(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The model call failed (HTTP " + ex.statusCode() + ").");
        problem.setTitle("Model Error");
        return problem;
    }

    @ExceptionHandler(AnthropicIoException.class)
    ProblemDetail handleModelUnreachable(AnthropicIoException ex) {
        logger.warn("Claude API unreachable", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Could not reach the model provider.");
        problem.setTitle("Model Unreachable");
        return problem;
    }

    /** Errors returned by the Gemini API. */
    @ExceptionHandler(ApiException.class)
    ProblemDetail handleGeminiError(ApiException ex) {
        logger.warn("Gemini API call failed with HTTP " + ex.code() + " " + ex.status(), ex);
        boolean rateLimited = ex.code() == 429;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                rateLimited ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                rateLimited ? "Gemini is rate limiting requests (free-tier quota?). Try again shortly."
                        : "The Gemini call failed (HTTP " + ex.code() + ").");
        problem.setTitle(rateLimited ? "Rate Limited" : "Model Error");
        return problem;
    }

    @ExceptionHandler(GenAiIOException.class)
    ProblemDetail handleGeminiUnreachable(GenAiIOException ex) {
        logger.warn("Gemini API unreachable", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Could not reach Gemini.");
        problem.setTitle("Model Unreachable");
        return problem;
    }

    /** Adds an {@code errors} map of field → message, e.g. {"comment": "must not be blank"}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
