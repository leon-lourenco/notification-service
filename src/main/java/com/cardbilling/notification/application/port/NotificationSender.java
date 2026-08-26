package com.cardbilling.notification.application.port;

import com.cardbilling.notification.domain.Notification;

/**
 * Actually delivers a notification.
 *
 * <p>The only implementation in this repository is a mock that logs, since a portfolio project has
 * no business holding real Twilio or SendGrid credentials. It is a port rather than a logging call
 * inside the use case so that the seam a real provider would plug into is visible, and so the
 * dispatch use case can be unit tested without asserting on log output.
 */
public interface NotificationSender {

    void send(Notification notification);
}
