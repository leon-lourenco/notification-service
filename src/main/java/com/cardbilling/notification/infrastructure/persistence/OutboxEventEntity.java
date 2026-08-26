package com.cardbilling.notification.infrastructure.persistence;

import com.cardbilling.notification.domain.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The persistence mapping for {@link OutboxEvent}.
 *
 * <p>{@code published_at} is indexed because the dispatcher's only query is "unpublished, oldest
 * first" and it runs every poll interval, forever. Without the index that poll degrades into a full
 * scan of a table that only grows.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(name = "idx_outbox_events_unpublished", columnList = "published_at, created_at"))
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
        // required by JPA
    }

    static OutboxEventEntity fromDomain(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = event.getId();
        entity.aggregateId = event.getAggregateId();
        entity.eventType = event.getEventType();
        entity.payload = event.getPayload();
        entity.createdAt = event.getCreatedAt();
        entity.publishedAt = event.getPublishedAt();
        return entity;
    }

    OutboxEvent toDomain() {
        return OutboxEvent.rehydrate(id, aggregateId, eventType, payload, createdAt, publishedAt);
    }

    void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
