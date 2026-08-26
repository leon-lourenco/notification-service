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
 * <p>The unique constraint on {@code (invoice_id, stage)} is the idempotency guarantee. Not a
 * convention, not a check the caller is trusted to perform: the database refuses a second row for a
 * stage already requested, so concurrent callers cannot both win.
 */
@Entity
@Table(
        name = "notifications",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_notifications_invoice_stage",
                        columnNames = {"invoice_id", "stage"}),
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
