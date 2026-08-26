package com.cardbilling.notification.infrastructure.messaging;

import com.cardbilling.notification.application.PublishPendingOutboxEventsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox on a fixed delay and drains it.
 *
 * <p>Polling rather than reacting to the write is the point, not a shortcut: reacting would tie the
 * publish back to the request that caused it and recreate the coupling the outbox exists to break.
 * A poller works from committed state, which means it also works for events written by a process
 * that has since crashed, or written while the broker was down.
 *
 * <p>{@code @Transactional} covers the batch, so a publish failure part-way rolls back the marks
 * for that pass; the events stay pending and the next poll retries them. The consequence is
 * at-least-once delivery, which the consumer is written to absorb.
 *
 * <p>The failure is logged as a readable one-liner and then rethrown, not swallowed: the rethrow is
 * what rolls the transaction back. An exception escaping a {@code @Scheduled} method suppresses
 * only that run and not the schedule, so a broker outage degrades into retries at the poll interval
 * rather than stopping dispatch.
 */
@Component
class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final PublishPendingOutboxEventsUseCase publishPendingEvents;
    private final int batchSize;

    OutboxDispatcher(
            PublishPendingOutboxEventsUseCase publishPendingEvents,
            @Value("${notification.outbox.batch-size:50}") int batchSize) {
        this.publishPendingEvents = publishPendingEvents;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.outbox.poll-interval-ms:1000}")
    @Transactional
    public void dispatchPendingEvents() {
        try {
            publishPendingEvents.publishPending(batchSize);
        } catch (RuntimeException e) {
            log.warn("Outbox dispatch pass failed - events stay pending and will be retried: {}", e.getMessage());
            throw e;
        }
    }
}
