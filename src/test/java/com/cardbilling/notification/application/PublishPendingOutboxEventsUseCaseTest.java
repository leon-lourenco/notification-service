package com.cardbilling.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardbilling.notification.application.port.NotificationEventPublisher;
import com.cardbilling.notification.domain.Notification;
import com.cardbilling.notification.domain.Notification.Channel;
import com.cardbilling.notification.domain.Notification.Stage;
import com.cardbilling.notification.domain.OutboxEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublishPendingOutboxEventsUseCaseTest {

    private InMemoryNotificationStore store;
    private RecordingPublisher publisher;
    private PublishPendingOutboxEventsUseCase publishPending;

    @BeforeEach
    void setUp() {
        store = new InMemoryNotificationStore();
        publisher = new RecordingPublisher();
        publishPending = new PublishPendingOutboxEventsUseCase(store, publisher);
    }

    @Test
    @DisplayName("pending events are published and then marked published")
    void publishesAndMarksPending() {
        OutboxEvent event = pendingEvent(108L);
        store.appendOutboxEvent(event);

        int published = publishPending.publishPending(50);

        assertThat(published).isEqualTo(1);
        assertThat(publisher.published).containsExactly(event);
        assertThat(store.findUnpublished(50)).isEmpty();
    }

    @Test
    @DisplayName("an already published event is not published again on the next pass")
    void doesNotRepublishOnTheNextPass() {
        store.appendOutboxEvent(pendingEvent(108L));

        publishPending.publishPending(50);
        int secondPass = publishPending.publishPending(50);

        assertThat(secondPass).isZero();
        assertThat(publisher.published).hasSize(1);
    }

    @Test
    @DisplayName("a failing publish leaves the event pending, so the next pass retries it")
    void failedPublishLeavesTheEventPending() {
        OutboxEvent event = pendingEvent(108L);
        store.appendOutboxEvent(event);
        publisher.failWith(new IllegalStateException("broker unreachable"));

        assertThatThrownBy(() -> publishPending.publishPending(50)).isInstanceOf(IllegalStateException.class);

        // Nothing was marked. An unreachable broker delays delivery; it does not lose the request,
        // which is the entire difference from the monolith's fire-and-forget send.
        assertThat(event.isPublished()).isFalse();
        assertThat(store.findUnpublished(50)).containsExactly(event);
    }

    @Test
    @DisplayName("events are published oldest first, so a notification's events stay in order")
    void publishesOldestFirst() {
        OutboxEvent first = pendingEvent(1L);
        OutboxEvent second = pendingEvent(2L);
        OutboxEvent third = pendingEvent(3L);
        store.appendOutboxEvent(third);
        store.appendOutboxEvent(first);
        store.appendOutboxEvent(second);

        publishPending.publishPending(50);

        assertThat(publisher.published)
                .extracting(OutboxEvent::getCreatedAt)
                .isSorted();
    }

    @Test
    @DisplayName("an empty outbox is a no-op")
    void emptyOutboxIsANoOp() {
        assertThat(publishPending.publishPending(50)).isZero();
        assertThat(publisher.published).isEmpty();
    }

    private static OutboxEvent pendingEvent(long invoiceId) {
        OutboxEvent event =
                OutboxEvent.notificationRequested(
                        Notification.request(42L, invoiceId, Channel.EMAIL, Stage.REMINDER_D5, null));
        sleepAMoment();
        return event;
    }

    /** Creation timestamps drive the publish order, and Instant.now() can repeat inside a tick. */
    private static void sleepAMoment() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RecordingPublisher implements NotificationEventPublisher {

        private final List<OutboxEvent> published = new ArrayList<>();
        private RuntimeException failure;

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void publish(OutboxEvent event) {
            if (failure != null) {
                throw failure;
            }
            published.add(event);
        }
    }
}
