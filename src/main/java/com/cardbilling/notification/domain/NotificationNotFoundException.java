package com.cardbilling.notification.domain;

import java.util.UUID;

/** Raised when a notification id does not resolve to a stored record. */
public final class NotificationNotFoundException extends NotificationDomainException {

    private final UUID notificationId;

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification %s not found".formatted(notificationId));
        this.notificationId = notificationId;
    }

    public UUID getNotificationId() {
        return notificationId;
    }
}
