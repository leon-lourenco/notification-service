package com.cardbilling.notification.infrastructure.web;

import com.cardbilling.notification.application.RequestNotificationCommand;
import com.cardbilling.notification.domain.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request body of {@code POST /notifications}. */
@Schema(name = "NotificationRequest", description = "A request to notify a customer about an invoice")
public record RequestNotificationRequest(
        @Schema(description = "Customer the notification is about, as identified by billing-service", example = "42")
                @NotNull
                @Positive
                Long customerId,
        @Schema(description = "Invoice the notification is about, as identified by billing-service", example = "108")
                @NotNull
                @Positive
                Long invoiceId,
        @Schema(description = "Delivery channel", example = "EMAIL") @NotNull Notification.Channel channel,
        @Schema(description = "Escalation stage the invoice has reached", example = "REMINDER_D5") @NotNull
                Notification.Stage stage,
        @Schema(
                        description =
                                "Optional delivery address - email or phone number. This service owns the dispatch "
                                        + "record, not the customer, so callers that know the address pass it and "
                                        + "callers that do not simply omit it.",
                        example = "maria.silva@example.com")
                @Size(max = 255)
                String recipient) {

    RequestNotificationCommand toCommand() {
        return new RequestNotificationCommand(customerId, invoiceId, channel, stage, recipient);
    }
}
