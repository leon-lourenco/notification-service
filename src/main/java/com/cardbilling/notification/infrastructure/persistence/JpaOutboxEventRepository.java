package com.cardbilling.notification.infrastructure.persistence;

import com.cardbilling.notification.application.port.OutboxEventRepository;
import com.cardbilling.notification.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** Adapter mapping {@link OutboxEventRepository} onto Spring Data JPA. */
@Repository
class JpaOutboxEventRepository implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEvents;

    JpaOutboxEventRepository(OutboxEventJpaRepository outboxEvents) {
        this.outboxEvents = outboxEvents;
    }

    @Override
    public List<OutboxEvent> findUnpublished(int limit) {
        return outboxEvents.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(limit)).stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    /**
     * Persists the {@code publishedAt} the caller already stamped on the domain object, so the two
     * views of the event cannot disagree about when it was published.
     */
    @Override
    public void markPublished(OutboxEvent event) {
        outboxEvents.save(OutboxEventEntity.fromDomain(event));
    }
}
