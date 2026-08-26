package com.cardbilling.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.notification.domain.Notification;
import com.cardbilling.notification.domain.Notification.Channel;
import com.cardbilling.notification.domain.Notification.Stage;
import com.cardbilling.notification.domain.Notification.Status;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DispatchNotificationUseCaseTest {

    private InMemoryNotificationStore store;
    private RecordingNotificationSender sender;
    private DispatchNotificationUseCase dispatchNotification;

    @BeforeEach
    void setUp() {
        store = new InMemoryNotificationStore();
        sender = new RecordingNotificationSender();
        dispatchNotification = new DispatchNotificationUseCase(store, sender);
    }

    @Test
    @DisplayName("dispatching sends the notification and records it as SENT")
    void dispatchSendsAndMarksSent() {
        Notification notification = store.save(
                Notification.request(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, "maria@example.com"));

        Optional<Notification> dispatched = dispatchNotification.dispatch(notification.getId());

        assertThat(sender.sent()).containsExactly(notification);
        assertThat(dispatched).isPresent();
        assertThat(dispatched.get().getStatus()).isEqualTo(Status.SENT);
        assertThat(store.findById(notification.getId()).orElseThrow().isSent()).isTrue();
    }

    @Test
    @DisplayName("a redelivered notification is not sent a second time")
    void redeliveryDoesNotSendTwice() {
        Notification notification =
                store.save(Notification.request(42L, 108L, Channel.SMS, Stage.REMINDER_D15, null));

        // The outbox gives at-least-once delivery: a crash between the broker's ack and the
        // published mark republishes the event. This guard is what makes that safe, and is the
        // reason the whole design can prefer duplicates over losses.
        dispatchNotification.dispatch(notification.getId());
        dispatchNotification.dispatch(notification.getId());
        dispatchNotification.dispatch(notification.getId());

        assertThat(sender.sent()).hasSize(1);
    }

    @Test
    @DisplayName("a redelivery keeps the original sent timestamp")
    void redeliveryKeepsTheOriginalTimestamp() {
        Notification notification =
                store.save(Notification.request(42L, 108L, Channel.EMAIL, Stage.FORMAL_NOTICE_D30, null));
        var firstSentAt = dispatchNotification.dispatch(notification.getId()).orElseThrow().getSentAt();

        var afterRedelivery = dispatchNotification.dispatch(notification.getId()).orElseThrow().getSentAt();

        assertThat(afterRedelivery).isEqualTo(firstSentAt);
    }

    @Test
    @DisplayName("an unknown id is skipped rather than failing the consumer forever")
    void unknownIdIsSkipped() {
        assertThat(dispatchNotification.dispatch(UUID.randomUUID())).isEmpty();
        assertThat(sender.sent()).isEmpty();
    }
}
