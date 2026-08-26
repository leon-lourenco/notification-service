package com.cardbilling.notification.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom metrics for notification service business operations.
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordNotificationRequested(Long invoiceId, String channel, String stage) {
        Counter.builder("notification.requested")
            .description("Number of notifications requested")
            .tag("channel", channel)
            .tag("stage", stage)
            .register(meterRegistry)
            .increment();
    }

    public void recordNotificationSent(Long invoiceId, String channel, long latencyMs) {
        Counter.builder("notification.sent")
            .description("Number of notifications sent")
            .tag("channel", channel)
            .register(meterRegistry)
            .increment();

        meterRegistry.timer("notification.send.latency", "channel", channel)
            .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordOutboxEventPublished(int eventCount, long latencyMs) {
        Counter.builder("outbox.published")
            .description("Number of outbox events published to Kafka")
            .register(meterRegistry)
            .increment(eventCount);

        meterRegistry.timer("outbox.publish.latency")
            .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordOutboxEventPending(int pendingCount) {
        meterRegistry.gauge("outbox.pending", pendingCount);
    }

    public void recordDuplicateNotificationAttempt(Long invoiceId, String stage) {
        Counter.builder("notification.duplicate.attempted")
            .description("Duplicate notification attempt (idempotent)")
            .tag("stage", stage)
            .register(meterRegistry)
            .increment();
    }

    public void recordNotificationFailure(String channel, String reason) {
        Counter.builder("notification.failures")
            .description("Number of notification failures")
            .tag("channel", channel)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }
}
