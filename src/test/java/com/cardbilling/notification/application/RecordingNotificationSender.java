package com.cardbilling.notification.application;

import com.cardbilling.notification.application.port.NotificationSender;
import com.cardbilling.notification.domain.Notification;
import java.util.ArrayList;
import java.util.List;

/** Records what was "delivered", so dispatch tests assert on behaviour instead of on log output. */
class RecordingNotificationSender implements NotificationSender {

    private final List<Notification> sent = new ArrayList<>();

    @Override
    public void send(Notification notification) {
        sent.add(notification);
    }

    List<Notification> sent() {
        return List.copyOf(sent);
    }
}
