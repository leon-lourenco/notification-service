package com.cardbilling.notification.infrastructure.messaging;

import com.cardbilling.notification.application.DispatchNotificationUseCase;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code notification.requested} and drives the simulated delivery.
 *
 * <p>The monolith's equivalent listener had to be {@code @Transactional} because it touched lazy
 * {@code Customer} and {@code Invoice} associations after the repository's session had closed, and
 * threw {@code LazyInitializationException} on its first live run until it was annotated. That
 * particular hazard cannot occur here - this bounded context holds ids, not associations, so there
 * is nothing lazy to resolve.
 *
 * <p>The annotation is still correct, for a different reason: the use case reads the notification,
 * decides whether to deliver, and writes the new status, and those three steps have to be one unit.
 * Without it the read and the write run in separate transactions, and a failure between them leaves
 * a notification that was delivered but is still recorded {@code REQUESTED} - which the next
 * redelivery would then deliver again.
 */
@Component
class NotificationDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryConsumer.class);

    private final DispatchNotificationUseCase dispatchNotification;

    NotificationDeliveryConsumer(DispatchNotificationUseCase dispatchNotification) {
        this.dispatchNotification = dispatchNotification;
    }

    @Transactional
    @KafkaListener(
            topics = "${notification.kafka.topic.notification-requested:" + NotificationTopics.NOTIFICATION_REQUESTED + "}",
            groupId = "${notification.kafka.consumer-group:notification-service}")
    public void onNotificationRequested(String payload) {
        UUID notificationId;
        try {
            notificationId = UUID.fromString(payload.trim());
        } catch (IllegalArgumentException e) {
            // Nothing downstream can make sense of this, and failing would redeliver it forever.
            log.error("Discarding unparseable notification.requested payload: '{}'", payload);
            return;
        }
        dispatchNotification.dispatch(notificationId);
    }
}
