package com.cardbilling.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.notification.domain.Notification.Channel;
import com.cardbilling.notification.domain.Notification.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    @DisplayName("an event for a requested notification points back at it and starts unpublished")
    void notificationRequestedReferencesTheNotification() {
        Notification notification = Notification.request(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null);

        OutboxEvent event = OutboxEvent.notificationRequested(notification);

        assertThat(event.getAggregateId()).isEqualTo(notification.getId());
        assertThat(event.getEventType()).isEqualTo(OutboxEvent.NOTIFICATION_REQUESTED);
        // The payload is the notification id: the consumer reads the same database, so a fuller
        // snapshot on the topic would only be a second, staler copy of state it can read directly.
        assertThat(event.getPayload()).isEqualTo(notification.getId().toString());
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("marking published stamps the moment the broker acknowledged it")
    void markPublishedStampsTheTime() {
        OutboxEvent event =
                OutboxEvent.notificationRequested(
                        Notification.request(42L, 108L, Channel.SMS, Stage.REMINDER_D15, null));

        event.markPublished();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("a rehydrated published event is still recognised as published")
    void rehydratePreservesPublishedState() {
        OutboxEvent event =
                OutboxEvent.notificationRequested(
                        Notification.request(42L, 108L, Channel.EMAIL, Stage.FORMAL_NOTICE_D30, null));
        event.markPublished();

        OutboxEvent restored =
                OutboxEvent.rehydrate(
                        event.getId(),
                        event.getAggregateId(),
                        event.getEventType(),
                        event.getPayload(),
                        event.getCreatedAt(),
                        event.getPublishedAt());

        assertThat(restored).isEqualTo(event);
        assertThat(restored.isPublished()).isTrue();
        assertThat(restored.getPublishedAt()).isEqualTo(event.getPublishedAt());
    }
}
