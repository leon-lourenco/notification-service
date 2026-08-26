package com.cardbilling.notification.infrastructure.persistence;

import com.cardbilling.notification.domain.Notification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * The persistence mapping for {@link Notification}. Separate from the domain type so the domain
 * stays free of JPA - which is what makes the ArchUnit rules in this repository mean something
 * rather than describe an aspiration.
 *
 * <p>The unique constraint on {@code (invoice_id, stage, channel)} is the idempotency guarantee. Not
 * a convention, not a check the caller is trusted to perform: the database refuses a second row for
 * a stage already requested on that channel, so concurrent callers cannot both win.
 *
 * <p>Channel belongs in the key. An escalation stage is reached once, but the monolith notified a
 * customer on more than one channel at that stage - an email and an SMS for the same D+5 reminder.
 * Keying on {@code (invoice_id, stage)} alone would make the second channel look like a retry of
 * the first and silently drop it, which is a quieter version of exactly the bug this service exists
 * to fix.
 */
@Entity
@Table(
        name = "notifications",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_notifications_invoice_stage_channel",
                        columnNames = {"invoice_id", "stage", "channel"}),
        indexes = @Index(name = "idx_notifications_status", columnList = "status"))
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private long customerId;

    @Column(name = "invoice_id", nullable = false)
    private long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Notification.Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Notification.Stage stage;

    @Column(length = 255)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Notification.Status status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationEntity() {
        // required by JPA
    }

    static NotificationEntity fromDomain(Notification notification) {
        NotificationEntity entity = new NotificationEntity();
        entity.id = notification.getId();
        entity.customerId = notification.getCustomerId();
        entity.invoiceId = notification.getInvoiceId();
        entity.channel = notification.getChannel();
        entity.stage = notification.getStage();
        entity.recipient = notification.getRecipient();
        entity.status = notification.getStatus();
        entity.requestedAt = notification.getRequestedAt();
        entity.sentAt = notification.getSentAt();
        return entity;
    }

    Notification toDomain() {
        return Notification.rehydrate(
                id, customerId, invoiceId, channel, stage, recipient, requestedAt, status, sentAt);
    }

    UUID getId() {
        return id;
    }
}
