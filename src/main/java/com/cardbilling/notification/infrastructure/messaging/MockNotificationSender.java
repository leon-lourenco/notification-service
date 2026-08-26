package com.cardbilling.notification.infrastructure.messaging;

import com.cardbilling.notification.application.port.NotificationSender;
import com.cardbilling.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real email or SMS provider, in the same shape the monolith used: one log line
 * saying the message left the building. There is no Twilio or SendGrid account behind this project,
 * and inventing one would make the delivery guarantee this service demonstrates unverifiable rather
 * than more convincing.
 *
 * <p>The line names the customer by id where the monolith named them by name. That is the bounded
 * context split showing through: {@code Customer} belongs to {@code billing-service} now, and this
 * service knows only what the request carried. Callers that pass the optional {@code recipient} get
 * the address in the line as well.
 */
@Component
class MockNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info(
                "[MOCK {}] To {}: invoice #{} is at stage {}",
                notification.getChannel(),
                addressee(notification),
                notification.getInvoiceId(),
                notification.getStage());
    }

    private static String addressee(Notification notification) {
        return notification.getRecipient() == null
                ? "customer " + notification.getCustomerId()
                : "%s (customer %d)".formatted(notification.getRecipient(), notification.getCustomerId());
    }
}
