package com.cardbilling.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A domain event captured in the same local transaction as the write that raised it.
 *
 * <p>This is the write side of the outbox pattern, and the whole reason this service exists. The
 * monolith's {@code NotificationRequestPublisher} saved its notification row and then called
 * {@code kafkaTemplate.send(...)} as a second, unrelated operation - a dual write. A crash, a
 * broker hiccup, or a rolled back transaction between the two left a {@code REQUESTED} row that
 * nothing would ever pick up again; a real run lost 12 of 42 requests exactly that way.
 *
 * <p>Writing this row instead of publishing means the transaction either commits both the
 * notification and the intent to publish it, or neither. Publishing then becomes a separate,
 * retryable step against durable state rather than a fire-and-forget side effect.
 */
public final class OutboxEvent {

    public static final String NOTIFICATION_REQUESTED = "NotificationRequested";

    private final UUID id;
    private final UUID aggregateId;
    private final String eventType;
    private final String payload;
    private final Instant createdAt;
    private Instant publishedAt;

    private OutboxEvent(
            UUID id,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant createdAt,
            Instant publishedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.publishedAt = publishedAt;
    }

    /**
     * The event raised when a notification is accepted.
     *
     * <p>The payload is just the notification id, matching what the monolith put on the topic. The
     * consumer is in this same service and reads the same database, so shipping a fuller snapshot
     * would only create a second, staler copy of state the consumer can read authoritatively.
     */
    public static OutboxEvent notificationRequested(Notification notification) {
        return new OutboxEvent(
                UUID.randomUUID(),
                notification.getId(),
                NOTIFICATION_REQUESTED,
                notification.getId().toString(),
                Instant.now(),
                null);
    }

    /** Rebuilds an event from stored state. Used by the persistence adapter only. */
    public static OutboxEvent rehydrate(
            UUID id,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant createdAt,
            Instant publishedAt) {
        return new OutboxEvent(id, aggregateId, eventType, payload, createdAt, publishedAt);
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OutboxEvent event && id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "OutboxEvent[id=%s, type=%s, aggregateId=%s, published=%s]"
                .formatted(id, eventType, aggregateId, isPublished());
    }
}
