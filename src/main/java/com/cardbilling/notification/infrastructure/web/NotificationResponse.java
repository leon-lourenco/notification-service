package com.cardbilling.notification.infrastructure.web;

import com.cardbilling.notification.domain.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Response body of {@code POST /notifications}, for both the accepted and the already-requested case. */
@Schema(name = "Notification", description = "A notification dispatch record")
public record NotificationResponse(
        UUID id,
        long customerId,
        long invoiceId,
        Notification.Channel channel,
        Notification.Stage stage,
        String recipient,
        Notification.Status status,
        Instant requestedAt,
        Instant sentAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getCustomerId(),
                notification.getInvoiceId(),
                notification.getChannel(),
                notification.getStage(),
                notification.getRecipient(),
                notification.getStatus(),
                notification.getRequestedAt(),
                notification.getSentAt());
    }
}
