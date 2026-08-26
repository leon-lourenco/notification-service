package com.cardbilling.notification.domain;

/**
 * Raised when a notification for an {@code (invoiceId, stage, channel)} triple already exists.
 *
 * <p>This is deliberately not an API failure. {@code POST /notifications} is idempotent on that
 * triple, so a caller repeating a request - a retry, a rerun of the collections cycle for the same
 * day - gets the record that already exists. The exception exists for the one case the pre-check
 * cannot cover: two concurrent requests for the same triple, where the database's unique constraint
 * is the only real arbiter. The use case catches it, re-reads, and returns the winner's record, so
 * this type is an internal control signal and an observability hook rather than something a client
 * ever sees.
 *
 * <p>Note that the channel is part of what makes a request a duplicate. Requesting an SMS for a
 * stage already notified by email is a second dispatch, not a repeat, and does not raise this.
 */
public final class DuplicateNotificationException extends NotificationDomainException {

    private final long invoiceId;
    private final Notification.Stage stage;
    private final Notification.Channel channel;

    public DuplicateNotificationException(
            long invoiceId, Notification.Stage stage, Notification.Channel channel, Throwable cause) {
        super(
                "Notification for invoice %d at stage %s on channel %s already exists"
                        .formatted(invoiceId, stage, channel),
                cause);
        this.invoiceId = invoiceId;
        this.stage = stage;
        this.channel = channel;
    }

    public long getInvoiceId() {
        return invoiceId;
    }

    public Notification.Stage getStage() {
        return stage;
    }

    public Notification.Channel getChannel() {
        return channel;
    }
}
