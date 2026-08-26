package com.cardbilling.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardbilling.notification.domain.DuplicateNotificationException;
import com.cardbilling.notification.domain.Notification;
import com.cardbilling.notification.domain.Notification.Channel;
import com.cardbilling.notification.domain.Notification.Stage;
import com.cardbilling.notification.domain.Notification.Status;
import com.cardbilling.notification.domain.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestNotificationUseCaseTest {

    private InMemoryNotificationStore store;
    private RequestNotificationUseCase requestNotification;

    @BeforeEach
    void setUp() {
        store = new InMemoryNotificationStore();
        requestNotification = new RequestNotificationUseCase(store, store);
    }

    @Test
    @DisplayName("a new request writes the notification and an outbox event together")
    void writesNotificationAndOutboxEventTogether() {
        NotificationRequestResult result =
                requestNotification.request(
                        new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, "maria@example.com"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.notification().getStatus()).isEqualTo(Status.REQUESTED);

        assertThat(store.allNotifications()).hasSize(1);
        assertThat(store.allOutboxEvents()).hasSize(1);

        OutboxEvent event = store.allOutboxEvents().getFirst();
        assertThat(event.getAggregateId()).isEqualTo(result.notification().getId());
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    @DisplayName("nothing is published from the request path - only recorded for the dispatcher")
    void publishesNothingItself() {
        requestNotification.request(
                new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null));

        // The use case is constructed with no publisher at all, so there is no way for it to have
        // reached a broker. That is the structural difference from the monolith's publisher, which
        // held a KafkaTemplate and called it on this exact path.
        assertThat(store.allOutboxEvents()).allMatch(event -> !event.isPublished());
    }

    @Test
    @DisplayName("the other channel for an already-requested stage is dispatched, not swallowed")
    void secondChannelForTheSameStageIsANewNotification() {
        NotificationRequestResult email =
                requestNotification.request(
                        new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null));
        NotificationRequestResult sms =
                requestNotification.request(
                        new RequestNotificationCommand(42L, 108L, Channel.SMS, Stage.REMINDER_D5, null));

        // The monolith notified a customer on both channels at the same escalation stage. Keying
        // idempotency on (invoiceId, stage) alone made the SMS look like a retry of the email and
        // dropped it - silently, since the caller got a cheerful 200 back with the email's record.
        assertThat(sms.accepted()).isTrue();
        assertThat(sms.notification().getId()).isNotEqualTo(email.notification().getId());
        assertThat(store.allNotifications()).hasSize(2);
        // Two outbox events, so both actually reach the dispatcher.
        assertThat(store.allOutboxEvents()).hasSize(2);
    }

    @Test
    @DisplayName("repeating a request for the same invoice, stage and channel returns the existing record")
    void repeatedRequestReturnsExistingRecord() {
        RequestNotificationCommand command =
                new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null);

        NotificationRequestResult first = requestNotification.request(command);
        NotificationRequestResult second = requestNotification.request(command);

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isFalse();
        assertThat(second.notification().getId()).isEqualTo(first.notification().getId());

        assertThat(store.allNotifications()).hasSize(1);
        // No second outbox event either: a retried request must not produce a second message.
        assertThat(store.allOutboxEvents()).hasSize(1);
    }

    @Test
    @DisplayName("a different stage for the same invoice is a genuinely new notification")
    void differentStageIsANewNotification() {
        requestNotification.request(
                new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null));
        NotificationRequestResult escalated =
                requestNotification.request(
                        new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D15, null));

        assertThat(escalated.accepted()).isTrue();
        assertThat(store.allNotifications()).hasSize(2);
        assertThat(store.allOutboxEvents()).hasSize(2);
    }

    @Test
    @DisplayName("losing a concurrent race returns the record the winner committed")
    void losingTheRaceReturnsTheWinnersRecord() {
        Notification winner = Notification.request(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null);
        store.loseNextRaceTo(winner);

        NotificationRequestResult result =
                requestNotification.request(
                        new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null));

        assertThat(result.accepted()).isFalse();
        assertThat(result.notification().getId()).isEqualTo(winner.getId());
    }

    @Test
    @DisplayName("a duplicate with no committed record to find is surfaced rather than faked")
    void unresolvableDuplicateIsSurfaced() {
        store.failNextWriteAsDuplicateWithNoWinner();

        assertThatThrownBy(
                        () ->
                                requestNotification.request(
                                        new RequestNotificationCommand(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null)))
                .isInstanceOf(DuplicateNotificationException.class);
    }
}
