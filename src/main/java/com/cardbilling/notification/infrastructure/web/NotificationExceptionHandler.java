package com.cardbilling.notification.infrastructure.web;

import com.cardbilling.notification.domain.DuplicateNotificationException;
import com.cardbilling.notification.domain.NotificationNotFoundException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this service's domain exceptions to RFC 7807 {@code application/problem+json}, using
 * Spring's built-in {@link ProblemDetail}. No generic 500 with a leaked stack trace leaves this
 * service.
 */
@RestControllerAdvice
class NotificationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://cardbilling.example/problems/";

    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail handleNotFound(NotificationNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Notification not found");
        problem.setType(URI.create(PROBLEM_BASE + "notification-not-found"));
        problem.setProperty("notificationId", e.getNotificationId().toString());
        return problem;
    }

    /**
     * Only reachable if the duplicate recovery in {@code RequestNotificationUseCase} could not find
     * the record that beat it - which would mean the winning transaction rolled back after all. The
     * honest answer then is "try again", not a fabricated success, so this is a 409 rather than the
     * 200 the ordinary duplicate path returns.
     */
    @ExceptionHandler(DuplicateNotificationException.class)
    ProblemDetail handleDuplicate(DuplicateNotificationException e) {
        log.warn(
                "Duplicate notification for invoice {} at stage {} could not be resolved to an existing record",
                e.getInvoiceId(),
                e.getStage());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "A concurrent request for this invoice and stage is still settling. Retry.");
        problem.setTitle("Duplicate notification request");
        problem.setType(URI.create(PROBLEM_BASE + "duplicate-notification"));
        problem.setProperty("invoiceId", e.getInvoiceId());
        problem.setProperty("stage", e.getStage().name());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String detail =
                e.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + " " + error.getDefaultMessage())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Request body failed validation");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid notification request");
        problem.setType(URI.create(PROBLEM_BASE + "invalid-request"));
        return problem;
    }
}
