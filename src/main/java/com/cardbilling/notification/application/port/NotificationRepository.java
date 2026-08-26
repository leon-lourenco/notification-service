package com.cardbilling.notification.application.port;

import com.cardbilling.notification.domain.Notification;
import java.util.Optional;
import java.util.UUID;

/** Reads and updates of notification dispatch records. Implemented in {@code infrastructure.persistence}. */
public interface NotificationRepository {

    Optional<Notification> findById(UUID id);

    /** The idempotency lookup: {@code (invoiceId, stage)} identifies at most one notification. */
    Optional<Notification> findByInvoiceIdAndStage(long invoiceId, Notification.Stage stage);

    Notification save(Notification notification);
}
