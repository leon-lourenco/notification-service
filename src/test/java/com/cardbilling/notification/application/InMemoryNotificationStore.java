package com.cardbilling.notification.application;

import com.cardbilling.notification.application.port.NotificationRepository;
import com.cardbilling.notification.application.port.NotificationRequestWriter;
import com.cardbilling.notification.application.port.OutboxEventRepository;
import com.cardbilling.notification.domain.DuplicateNotificationException;
import com.cardbilling.notification.domain.Notification;
import com.cardbilling.notification.domain.OutboxEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One in-memory stand-in for all three persistence ports, enforcing the same
 * {@code (invoiceId, stage)} uniqueness the database does.
 *
 * <p>Hand-written rather than mocked because these tests are about how the use cases behave against
 * a store that actually rejects duplicates - a mock told to throw would be asserting the test
 * author's assumption about the constraint rather than exercising it.
 */
class InMemoryNotificationStore
        implements NotificationRepository, NotificationRequestWriter, OutboxEventRepository {

    private final Map<UUID, Notification> notifications = new LinkedHashMap<>();
    private final Map<UUID, OutboxEvent> outboxEvents = new LinkedHashMap<>();

    /** Set to make the next atomic write lose the race, as a concurrent committer would cause. */
    private boolean failNextWriteAsDuplicate;

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(notifications.get(id));
    }

    @Override
    public Optional<Notification> findByInvoiceIdAndStage(long invoiceId, Notification.Stage stage) {
        return notifications.values().stream()
                .filter(n -> n.getInvoiceId() == invoiceId && n.getStage() == stage)
                .findFirst();
    }

    @Override
    public Notification save(Notification notification) {
        notifications.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public Notification writeAtomically(Notification notification, OutboxEvent event) {
        if (failNextWriteAsDuplicate) {
            failNextWriteAsDuplicate = false;
            throw new DuplicateNotificationException(
                    notification.getInvoiceId(), notification.getStage(), new IllegalStateException("test"));
        }
        if (findByInvoiceIdAndStage(notification.getInvoiceId(), notification.getStage()).isPresent()) {
            throw new DuplicateNotificationException(
                    notification.getInvoiceId(), notification.getStage(), new IllegalStateException("test"));
        }
        notifications.put(notification.getId(), notification);
        outboxEvents.put(event.getId(), event);
        return notification;
    }

    @Override
    public List<OutboxEvent> findUnpublished(int limit) {
        return outboxEvents.values().stream()
                .filter(event -> !event.isPublished())
                .sorted(Comparator.comparing(OutboxEvent::getCreatedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public void markPublished(OutboxEvent event) {
        outboxEvents.put(event.getId(), event);
    }

    void appendOutboxEvent(OutboxEvent event) {
        outboxEvents.put(event.getId(), event);
    }

    /**
     * Simulates a concurrent committer winning the unique constraint: the next atomic write fails,
     * and the record that "won" is already present for the recovery path to find.
     */
    void loseNextRaceTo(Notification winner) {
        notifications.put(winner.getId(), winner);
        failNextWriteAsDuplicate = true;
    }

    void failNextWriteAsDuplicateWithNoWinner() {
        failNextWriteAsDuplicate = true;
    }

    List<Notification> allNotifications() {
        return new ArrayList<>(notifications.values());
    }

    List<OutboxEvent> allOutboxEvents() {
        return new ArrayList<>(outboxEvents.values());
    }
}
