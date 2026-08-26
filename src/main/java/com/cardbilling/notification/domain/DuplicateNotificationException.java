package com.cardbilling.notification.domain;

/**
 * Raised when a notification for an {@code (invoiceId, stage)} pair already exists.
 *
 * <p>This is deliberately not an API failure. {@code POST /notifications} is idempotent on
 * {@code (invoiceId, stage)}, so a caller repeating a request - a retry, a rerun of the collections
 * cycle for the same day - gets the record that already exists. The exception exists for the one
 * case the pre-check cannot cover: two concurrent requests for the same pair, where the database's
 * unique constraint is the only real arbiter. The use case catches it, re-reads, and returns the
 * winner's record, so this type is an internal control signal and an observability hook rather than
 * something a client ever sees.
 */
public final class DuplicateNotificationException extends NotificationDomainException {

    private final long invoiceId;
    private final Notification.Stage stage;

    public DuplicateNotificationException(long invoiceId, Notification.Stage stage, Throwable cause) {
        super("Notification for invoice %d at stage %s already exists".formatted(invoiceId, stage), cause);
        this.invoiceId = invoiceId;
        this.stage = stage;
    }

    public long getInvoiceId() {
        return invoiceId;
    }

    public Notification.Stage getStage() {
        return stage;
    }
}
