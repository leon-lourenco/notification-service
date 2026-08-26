package com.cardbilling.notification.application;

import com.cardbilling.notification.application.port.NotificationRepository;
import com.cardbilling.notification.application.port.NotificationRequestWriter;
import com.cardbilling.notification.domain.DuplicateNotificationException;
import com.cardbilling.notification.domain.Notification;
import com.cardbilling.notification.domain.OutboxEvent;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Accepts a notification request durably.
 *
 * <p>This is the use case that closes the monolith's gap. The monolith did:
 *
 * <pre>
 *   notificationRepository.save(notification);
 *   kafkaTemplate.send(TOPIC, notification.getId().toString());
 * </pre>
 *
 * two independent operations, the second of which could simply not happen. Here the notification
 * and an {@link OutboxEvent} recording the intent to publish it are written together, and nothing
 * is published from this path at all - {@code OutboxDispatcher} does that afterwards, from
 * committed state it can retry against. By the time this method returns, the request survives a
 * broker outage, a crash, or a restart.
 *
 * <p>Deliberately not transactional. The atomic part is delegated to
 * {@link NotificationRequestWriter}, which owns its own transaction; the duplicate recovery below
 * has to run outside it, because a unique constraint violation aborts the transaction it occurs in
 * and a re-read in that same transaction would fail too.
 */
@Service
public class RequestNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestNotificationUseCase.class);

    private final NotificationRepository notifications;
    private final NotificationRequestWriter requestWriter;

    public RequestNotificationUseCase(
            NotificationRepository notifications, NotificationRequestWriter requestWriter) {
        this.notifications = notifications;
        this.requestWriter = requestWriter;
    }

    public NotificationRequestResult request(RequestNotificationCommand command) {
        // Keyed on channel as well as invoice and stage: one escalation stage can legitimately be
        // notified on more than one channel, so an SMS for a stage already emailed is a second
        // dispatch rather than a retry of the first.
        Optional<Notification> existing =
                notifications.findByInvoiceIdAndStageAndChannel(
                        command.invoiceId(), command.stage(), command.channel());
        if (existing.isPresent()) {
            log.debug(
                    "Notification for invoice {} at stage {} on {} already requested - returning existing record {}",
                    command.invoiceId(),
                    command.stage(),
                    command.channel(),
                    existing.get().getId());
            return NotificationRequestResult.alreadyRequested(existing.get());
        }

        Notification notification =
                Notification.request(
                        command.customerId(),
                        command.invoiceId(),
                        command.channel(),
                        command.stage(),
                        command.recipient());

        try {
            Notification saved =
                    requestWriter.writeAtomically(
                            notification, OutboxEvent.notificationRequested(notification));
            log.info(
                    "Accepted notification {} for invoice {} at stage {} on {} - outbox event written in the same transaction",
                    saved.getId(),
                    saved.getInvoiceId(),
                    saved.getStage(),
                    saved.getChannel());
            return NotificationRequestResult.accepted(saved);
        } catch (DuplicateNotificationException e) {
            // Two callers raced for the same (invoiceId, stage, channel) and the unique constraint
            // settled it. That is the constraint doing its job, not a failure: re-read and answer
            // with whatever the winner committed, which is what the idempotency contract promises.
            log.info(
                    "Concurrent duplicate request for invoice {} at stage {} on {} - returning the committed record",
                    command.invoiceId(),
                    command.stage(),
                    command.channel());
            return notifications
                    .findByInvoiceIdAndStageAndChannel(
                            command.invoiceId(), command.stage(), command.channel())
                    .map(NotificationRequestResult::alreadyRequested)
                    .orElseThrow(() -> e);
        }
    }
}
