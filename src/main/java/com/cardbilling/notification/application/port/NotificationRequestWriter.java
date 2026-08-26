package com.cardbilling.notification.application.port;

import com.cardbilling.notification.domain.DuplicateNotificationException;
import com.cardbilling.notification.domain.Notification;
import com.cardbilling.notification.domain.OutboxEvent;

/**
 * The atomic write this whole service is built around.
 *
 * <p>It is a port of its own, separate from {@link NotificationRepository}, because atomicity here
 * is an application-level requirement - "the notification and the intent to publish it commit
 * together, or neither does" - while the transaction that delivers it is a persistence detail. The
 * monolith had no equivalent seam: it saved, then published, and nothing in the code said the two
 * belonged together.
 *
 * <p>Keeping it out of the use case also keeps the use case free to run its duplicate recovery
 * outside any transaction, which matters: a unique constraint violation poisons the transaction it
 * happens in, so the re-read that follows one has to happen in a fresh one.
 */
public interface NotificationRequestWriter {

    /**
     * Persists {@code notification} and {@code event} in a single local transaction.
     *
     * @throws DuplicateNotificationException if a notification for the same
     *     {@code (invoiceId, stage)} pair was committed concurrently
     */
    Notification writeAtomically(Notification notification, OutboxEvent event);
}
