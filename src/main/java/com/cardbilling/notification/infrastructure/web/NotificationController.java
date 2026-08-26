package com.cardbilling.notification.infrastructure.web;

import com.cardbilling.notification.application.NotificationRequestResult;
import com.cardbilling.notification.application.RequestNotificationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint this service exposes, called synchronously by {@code collections-service}.
 *
 * <p>Note what it does not do: it does not talk to Kafka. The response means "durably accepted",
 * not "sent", and those are now different claims backed by different mechanisms. A broker outage
 * during this call changes nothing about its outcome.
 */
@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications", description = "Requesting notification dispatch")
class NotificationController {

    private final RequestNotificationUseCase requestNotification;

    NotificationController(RequestNotificationUseCase requestNotification) {
        this.requestNotification = requestNotification;
    }

    @PostMapping
    @Operation(
            summary = "Request a notification",
            description =
                    "Writes the notification and its outbox event in one local transaction and returns "
                            + "immediately. Idempotent on (invoiceId, stage): a repeat request returns the "
                            + "record that already exists rather than creating a second one or failing.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted - the notification was recorded and will be dispatched"),
        @ApiResponse(responseCode = "200", description = "This invoice/stage was already requested; the existing record is returned"),
        @ApiResponse(responseCode = "400", description = "Malformed request")
    })
    ResponseEntity<NotificationResponse> request(@Valid @RequestBody RequestNotificationRequest request) {
        NotificationRequestResult result = requestNotification.request(request.toCommand());
        return ResponseEntity.status(result.accepted() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(NotificationResponse.from(result.notification()));
    }
}
