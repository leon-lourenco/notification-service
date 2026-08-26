package com.cardbilling.notification.application.port;

import com.cardbilling.notification.domain.OutboxEvent;
import java.util.List;

/** The read/mark side of the outbox. The write side is {@link NotificationRequestWriter}. */
public interface OutboxEventRepository {

    /** Unpublished events, oldest first, so ordering per aggregate is preserved on replay. */
    List<OutboxEvent> findUnpublished(int limit);

    void markPublished(OutboxEvent event);
}
