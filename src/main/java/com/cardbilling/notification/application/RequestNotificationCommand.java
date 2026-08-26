package com.cardbilling.notification.application;

import com.cardbilling.notification.domain.Notification;

/**
 * What a caller is asking for. {@code recipient} is optional - see
 * {@link Notification#request(long, long, Notification.Channel, Notification.Stage, String)}.
 */
public record RequestNotificationCommand(
        long customerId,
        long invoiceId,
        Notification.Channel channel,
        Notification.Stage stage,
        String recipient) {}
