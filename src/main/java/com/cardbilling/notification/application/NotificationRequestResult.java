package com.cardbilling.notification.application;

import com.cardbilling.notification.domain.Notification;

/**
 * The outcome of a notification request, and whether it created anything.
 *
 * <p>The distinction is the API contract, not bookkeeping: a request that was accepted answers
 * {@code 202}, while one that matched an existing {@code (invoiceId, stage)} answers {@code 200}
 * with the record that already exists. Returning the flag rather than having the use case build a
 * response keeps HTTP concerns out of the application layer.
 */
public record NotificationRequestResult(Notification notification, boolean accepted) {

    public static NotificationRequestResult accepted(Notification notification) {
        return new NotificationRequestResult(notification, true);
    }

    public static NotificationRequestResult alreadyRequested(Notification notification) {
        return new NotificationRequestResult(notification, false);
    }
}
