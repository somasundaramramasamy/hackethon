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

import com.cognizant.backend.llm.LlmException;
import com.cognizant.backend.llm.LlmNotConfiguredException;
import com.cognizant.backend.run.InvalidRunRequestException;
import com.cognizant.backend.run.RunAlreadyActiveException;
import com.cognizant.backend.scenario.InvalidScenarioException;
import com.cognizant.backend.scenario.ScenarioInUseException;

/**
 * Returns errors as RFC 9457 problem details. The base class already covers malformed JSON (400)
 * and unsupported methods (405).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(ScenarioInUseException.class)
    ProblemDetail handleScenarioInUse(ScenarioInUseException ex) {
        return problem(HttpStatus.CONFLICT, "Scenario In Use", ex.getMessage());
    }

    @ExceptionHandler(InvalidScenarioException.class)
    ProblemDetail handleInvalidScenario(InvalidScenarioException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid Scenario", ex.getMessage());
        problem.setProperty("errors", ex.getErrors());
        return problem;
    }

    @ExceptionHandler(InvalidRunRequestException.class)
    ProblemDetail handleInvalidRun(InvalidRunRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Request", ex.getMessage());
    }

    @ExceptionHandler(RunAlreadyActiveException.class)
    ProblemDetail handleRunActive(RunAlreadyActiveException ex) {
        return problem(HttpStatus.CONFLICT, "Run In Progress", ex.getMessage());
    }

    /** A state rule was broken, e.g. approving a version that is not proposed. */
    @ExceptionHandler(NotAllowedException.class)
    ProblemDetail handleConflict(NotAllowedException ex) {
        return problem(HttpStatus.CONFLICT, "Not Allowed Now", ex.getMessage());
    }

    @ExceptionHandler(LlmNotConfiguredException.class)
    ProblemDetail handleNotConfigured(LlmNotConfiguredException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model Not Configured", ex.getMessage());
    }

    @ExceptionHandler(LlmException.class)
    ProblemDetail handleModelError(LlmException ex) {
        logger.warn("Model call failed: " + ex.getMessage(), ex);
        boolean rateLimited = ex.getStatus() == 429;
        return problem(rateLimited ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                rateLimited ? "Rate Limited" : "Model Error",
                rateLimited ? "The model provider is rate limiting requests. Try again shortly."
                        : "The model call failed: " + ex.getMessage());
    }

    /** Adds an {@code errors} map of field → message, e.g. {"reviewer": "must not be blank"}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
