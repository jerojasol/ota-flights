package io.github.jerojasol.flights.infrastructure.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Errors as RFC 9457 Problem Details:
 * <ul>
 *   <li>400: invalid parameters or body ({@code errors}: field → message), or an invalid cursor;</li>
 *   <li>409: a refresh while the aggregation lock is held or unavailable;</li>
 *   <li>422: a well-formed request the domain rejects while it is mapped (e.g. origin equals destination);</li>
 *   <li>500: anything else, including any other DomainException (a server-side fault, e.g. a corrupt stored row),
 *       with a {@code correlationId} that is also logged; no stack trace and no domain message in the body.</li>
 * </ul>
 * Spring MVC's own failures (missing body, wrong method, …) keep the base class's Problem Details.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException invalid,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : invalid.getBindingResult().getFieldErrors()) {
            String message = error.isBindingFailure() ? "has an invalid value" : error.getDefaultMessage();
            errors.merge(error.getField(), message, (first, second) -> first + "; " + second);
        }
        return ResponseEntity.badRequest().body(badRequest("Request parameters are invalid", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException invalid) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : invalid.getConstraintViolations()) {
            errors.merge(lastNode(violation.getPropertyPath().toString()), violation.getMessage(),
                    (first, second) -> first + "; " + second);
        }
        return badRequest("Request parameters are invalid", errors);
    }

    @ExceptionHandler(InvalidCursorException.class)
    ProblemDetail invalidCursor(InvalidCursorException invalid) {
        return badRequest("The cursor is not valid; start again from the first page",
                Map.of("cursor", invalid.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException rejected) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, rejected.getMessage());
        problem.setTitle("Request cannot be processed");
        return problem;
    }

    @ExceptionHandler(AggregationInProgressException.class)
    ProblemDetail aggregationInProgress(AggregationInProgressException busy) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, busy.getMessage());
        problem.setTitle("Aggregation not started");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception failure) {
        String correlationId = UUID.randomUUID().toString();
        LOG.error("web outcome=UNEXPECTED_ERROR correlation_id={}", correlationId, failure);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error. Quote the correlation id if you report it.");
        problem.setTitle("Internal error");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    private static ProblemDetail badRequest(String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}
