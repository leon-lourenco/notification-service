package com.cardbilling.notification.infrastructure.messaging;

import com.cardbilling.notification.application.port.NotificationEventPublisher;
import com.cardbilling.notification.domain.OutboxEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox events to Redpanda over the Kafka API, keyed by the notification id so every
 * event for one notification lands on the same partition and stays in order.
 *
 * <p>The {@code get(...)} is the important line. {@code KafkaTemplate.send} is asynchronous, and
 * returning on the future rather than on the acknowledgement would let the dispatcher mark an event
 * published before the broker had it - the same "wrote it down, assumed it was sent" mistake the
 * monolith made, reintroduced inside the mechanism built to prevent it. Blocking here costs a
 * round trip per event and buys the guarantee the whole service is about.
 */
@Component
class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long ackTimeoutSeconds;

    KafkaNotificationEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${notification.kafka.topic.notification-requested:notification.requested}") String topic,
            @Value("${notification.kafka.ack-timeout-seconds:5}") long ackTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.ackTimeoutSeconds = ackTimeoutSeconds;
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            kafkaTemplate
                    .send(topic, event.getAggregateId().toString(), event.getPayload())
                    .get(ackTimeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish outbox event " + event.getId() + " to Kafka", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event " + event.getId(), e);
        }
    }
}
