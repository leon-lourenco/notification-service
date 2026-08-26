package com.cardbilling.notification.application;

import com.cardbilling.notification.application.port.NotificationEventPublisher;
import com.cardbilling.notification.application.port.OutboxEventRepository;
import com.cardbilling.notification.domain.OutboxEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drains the outbox: publish, then mark published.
 *
 * <p>The ordering is the point. Publishing first means a crash between the ack and the mark leaves
 * the event pending and it gets republished - a duplicate, which the consumer's idempotency guard
 * absorbs. Marking first would mean a crash loses the publish silently, which is the dual-write
 * bug this service was built to remove, just moved one step later.
 *
 * <p>A failing publish propagates. The caller runs this in a transaction, so the whole batch's
 * marks roll back and the next poll retries from durable state; an unreachable broker delays
 * delivery rather than dropping it.
 */
@Service
public class PublishPendingOutboxEventsUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishPendingOutboxEventsUseCase.class);

    private final OutboxEventRepository outboxEvents;
    private final NotificationEventPublisher publisher;

    public PublishPendingOutboxEventsUseCase(
            OutboxEventRepository outboxEvents, NotificationEventPublisher publisher) {
        this.outboxEvents = outboxEvents;
        this.publisher = publisher;
    }

    /** @return how many events were published in this pass */
    public int publishPending(int batchSize) {
        List<OutboxEvent> pending = outboxEvents.findUnpublished(batchSize);
        if (pending.isEmpty()) {
            return 0;
        }

        for (OutboxEvent event : pending) {
            publisher.publish(event);
            event.markPublished();
            outboxEvents.markPublished(event);
        }

        log.info("Published {} outbox event(s)", pending.size());
        return pending.size();
    }
}
