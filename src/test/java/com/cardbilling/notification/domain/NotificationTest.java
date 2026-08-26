package com.cardbilling.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardbilling.notification.domain.Notification.Channel;
import com.cardbilling.notification.domain.Notification.Stage;
import com.cardbilling.notification.domain.Notification.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    @DisplayName("a requested notification starts REQUESTED, with an identity assigned before it is stored")
    void requestStartsRequestedWithAnIdentity() {
        Notification notification = Notification.request(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null);

        assertThat(notification.getStatus()).isEqualTo(Status.REQUESTED);
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getRequestedAt()).isNotNull();
        // The id exists before any database round trip, which is what lets the outbox event
        // referencing it be built and committed in the same transaction.
        assertThat(notification.getId()).isNotNull();
    }

    @Test
    @DisplayName("a blank recipient is stored as absent rather than as an empty address")
    void blankRecipientBecomesNull() {
        assertThat(Notification.request(42L, 108L, Channel.SMS, Stage.REMINDER_D15, "   ").getRecipient())
                .isNull();
        assertThat(Notification.request(42L, 108L, Channel.SMS, Stage.REMINDER_D15, " +5511999999999 ").getRecipient())
                .isEqualTo("+5511999999999");
    }

    @Test
    @DisplayName("marking sent records the status and the moment it happened")
    void markSentRecordsWhen() {
        Notification notification = Notification.request(42L, 108L, Channel.EMAIL, Stage.FORMAL_NOTICE_D30, null);

        notification.markSent();

        assertThat(notification.isSent()).isTrue();
        assertThat(notification.getStatus()).isEqualTo(Status.SENT);
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("sending twice is refused, so a bypassed idempotency guard fails loudly")
    void markSentTwiceIsRefused() {
        Notification notification = Notification.request(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null);
        notification.markSent();

        assertThatThrownBy(notification::markSent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already sent");
    }

    @Test
    @DisplayName("rehydration restores stored state exactly, including a sent timestamp")
    void rehydrateRestoresStoredState() {
        Notification original = Notification.request(7L, 9L, Channel.SMS, Stage.REMINDER_D15, "+5511999999999");
        original.markSent();

        Notification restored =
                Notification.rehydrate(
                        original.getId(),
                        original.getCustomerId(),
                        original.getInvoiceId(),
                        original.getChannel(),
                        original.getStage(),
                        original.getRecipient(),
                        original.getRequestedAt(),
                        original.getStatus(),
                        original.getSentAt());

        assertThat(restored).isEqualTo(original);
        assertThat(restored.isSent()).isTrue();
        assertThat(restored.getSentAt()).isEqualTo(original.getSentAt());
        assertThat(restored.getRecipient()).isEqualTo("+5511999999999");
    }

    @Test
    @DisplayName("identity is the id, not the field values")
    void identityIsTheId() {
        Notification one = Notification.request(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null);
        Notification other = Notification.request(42L, 108L, Channel.EMAIL, Stage.REMINDER_D5, null);

        assertThat(one).isNotEqualTo(other);
        assertThat(one).isEqualTo(
                Notification.rehydrate(
                        one.getId(), 1L, 2L, Channel.SMS, Stage.REMINDER_D15, null,
                        one.getRequestedAt(), Status.FAILED, null));
    }
}
