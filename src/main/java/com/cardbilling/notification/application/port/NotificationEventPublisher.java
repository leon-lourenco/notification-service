package com.cardbilling.notification.application.port;

import com.cardbilling.notification.domain.OutboxEvent;

/**
 * Publishes an outbox event to the broker. Implemented over Kafka in
 * {@code infrastructure.messaging}.
 *
 * <p>Implementations must publish synchronously - return only once the broker has acknowledged the
 * record, throw otherwise. The dispatcher marks an event published on the strength of that return,
 * so an implementation that returned before the ack would reintroduce the exact gap the outbox
 * exists to close.
 */
public interface NotificationEventPublisher {

    void publish(OutboxEvent event);
}
