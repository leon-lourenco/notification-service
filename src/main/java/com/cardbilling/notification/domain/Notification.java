package com.cardbilling.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One notification dispatch - requested by an upstream service, moved to {@code SENT} once the
 * delivery consumer has processed it.
 *
 * <p>Two things differ from the monolith's {@code Notification}, both consequences of the bounded
 * context split rather than style choices:
 *
 * <ul>
 *   <li>It holds a {@code customerId} and an {@code invoiceId}, not {@code Customer} and
 *       {@code Invoice} associations. Those aggregates belong to {@code billing-service} now, so
 *       there is nothing here to lazily load - which is also why the
 *       {@code LazyInitializationException} the monolith hit in its delivery consumer cannot
 *       occur in this service at all.
 *   <li>Its identity is a {@link UUID} assigned here, in the domain, rather than a database
 *       sequence value. That is what lets the outbox event referencing this notification be built
 *       and written in the same transaction as the notification itself, without a round trip to
 *       the database first to find out what id it was given.
 * </ul>
 */
public final class Notification {

    public enum Channel {
        EMAIL,
        SMS
    }

    /** Escalation stage, on the monolith's D+5 / D+15 / D+30 thresholds. */
    public enum Stage {
        REMINDER_D5,
        REMINDER_D15,
        FORMAL_NOTICE_D30
    }

    public enum Status {
        REQUESTED,
        SENT,
        FAILED
    }

    private final UUID id;
    private final long customerId;
    private final long invoiceId;
    private final Channel channel;
    private final Stage stage;
    private final String recipient;
    private final Instant requestedAt;
    private Status status;
    private Instant sentAt;

    private Notification(
            UUID id,
            long customerId,
            long invoiceId,
            Channel channel,
            Stage stage,
            String recipient,
            Instant requestedAt,
            Status status,
            Instant sentAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = customerId;
        this.invoiceId = invoiceId;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.recipient = recipient;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.sentAt = sentAt;
    }

    /**
     * A newly requested notification. {@code recipient} is the delivery address (email address or
     * phone number) and is optional: this service owns the dispatch record, not the customer, so a
     * caller that does not know the address simply omits it and the mocked delivery falls back to
     * identifying the customer by id.
     */
    public static Notification request(
            long customerId, long invoiceId, Channel channel, Stage stage, String recipient) {
        return new Notification(
                UUID.randomUUID(),
                customerId,
                invoiceId,
                channel,
                stage,
                normalise(recipient),
                Instant.now(),
                Status.REQUESTED,
                null);
    }

    /** Rebuilds a notification from stored state. Used by the persistence adapter only. */
    public static Notification rehydrate(
            UUID id,
            long customerId,
            long invoiceId,
            Channel channel,
            Stage stage,
            String recipient,
            Instant requestedAt,
            Status status,
            Instant sentAt) {
        return new Notification(
                id, customerId, invoiceId, channel, stage, recipient, requestedAt, status, sentAt);
    }

    private static String normalise(String recipient) {
        if (recipient == null) {
            return null;
        }
        String trimmed = recipient.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Records that delivery was simulated successfully.
     *
     * @throws IllegalStateException if this notification was already sent - the delivery consumer
     *     checks {@link #isSent()} first and skips redelivery, so reaching this means the
     *     idempotency guard was bypassed, which is a bug worth failing on rather than papering
     *     over with a second {@code sentAt} timestamp.
     */
    public void markSent() {
        if (status == Status.SENT) {
            throw new IllegalStateException("Notification " + id + " was already sent at " + sentAt);
        }
        this.status = Status.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public boolean isSent() {
        return status == Status.SENT;
    }

    public UUID getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getInvoiceId() {
        return invoiceId;
    }

    public Channel getChannel() {
        return channel;
    }

    public Stage getStage() {
        return stage;
    }

    /** The delivery address, or {@code null} when the caller did not supply one. */
    public String getRecipient() {
        return recipient;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Notification notification && id.equals(notification.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Notification[id=%s, invoiceId=%d, stage=%s, channel=%s, status=%s]"
                .formatted(id, invoiceId, stage, channel, status);
    }
}
