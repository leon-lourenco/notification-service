package com.cardbilling.notification.application.port;

import com.cardbilling.notification.domain.Notification;
import java.util.Optional;
import java.util.UUID;

/** Reads and updates of notification dispatch records. Implemented in {@code infrastructure.persistence}. */
public interface NotificationRepository {

    Optional<Notification> findById(UUID id);

    /**
     * The idempotency lookup: {@code (invoiceId, stage, channel)} identifies at most one
     * notification. Channel is part of the key because one escalation stage legitimately produces
     * one notification per channel - an email and an SMS for the same D+5 reminder are two
     * dispatches, not a duplicate.
     */
    Optional<Notification> findByInvoiceIdAndStageAndChannel(
            long invoiceId, Notification.Stage stage, Notification.Channel channel);

    Notification save(Notification notification);
}
