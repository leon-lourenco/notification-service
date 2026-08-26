package com.cardbilling.notification.application;

import com.cardbilling.notification.application.port.NotificationRepository;
import com.cardbilling.notification.application.port.NotificationSender;
import com.cardbilling.notification.domain.Notification;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Delivers a requested notification and records that it went out.
 *
 * <p>Called by the Kafka consumer, once per delivered record. The outbox gives at-least-once
 * delivery, not exactly-once - the dispatcher publishes first and marks the event published second,
 * so a crash in between republishes an event the broker already has. That trade is intentional: the
 * alternative ordering would mark events published that never reached the broker, which is the
 * monolith's bug again. Handling the consequence belongs here, and it is the {@link
 * Notification#isSent()} guard below: redelivery of an already-sent notification is a no-op instead
 * of a second message to the customer.
 */
@Service
public class DispatchNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchNotificationUseCase.class);

    private final NotificationRepository notifications;
    private final NotificationSender sender;

    public DispatchNotificationUseCase(NotificationRepository notifications, NotificationSender sender) {
        this.notifications = notifications;
        this.sender = sender;
    }

    /**
     * @return the notification as it now stands, or empty if the id resolves to nothing. An unknown
     *     id is not an error worth failing the consumer over - failing would only redeliver the same
     *     unresolvable record forever - so it is logged and skipped, exactly as the monolith's
     *     consumer did.
     */
    public Optional<Notification> dispatch(UUID notificationId) {
        Optional<Notification> found = notifications.findById(notificationId);
        if (found.isEmpty()) {
            log.warn("Notification {} not found - skipping", notificationId);
            return Optional.empty();
        }

        Notification notification = found.get();
        if (notification.isSent()) {
            log.debug(
                    "Notification {} was already sent at {} - skipping redelivery",
                    notificationId,
                    notification.getSentAt());
            return Optional.of(notification);
        }

        sender.send(notification);
        notification.markSent();
        return Optional.of(notifications.save(notification));
    }
}
