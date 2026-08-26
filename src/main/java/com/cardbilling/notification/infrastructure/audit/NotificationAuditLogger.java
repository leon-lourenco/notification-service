package com.cardbilling.notification.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit logger for recording notification business events.
 */
@Component
public class NotificationAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(NotificationAuditLogger.class);

    public void logNotificationRequested(Long invoiceId, Long customerId, String channel, String stage) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "NOTIFICATION_REQUESTED");
        event.put("invoiceId", invoiceId);
        event.put("customerId", customerId);
        event.put("channel", channel);
        event.put("stage", stage);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Notification requested", event);
    }

    public void logNotificationSent(Long invoiceId, String channel, String status) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "NOTIFICATION_SENT");
        event.put("invoiceId", invoiceId);
        event.put("channel", channel);
        event.put("status", status);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Notification sent", event);
    }

    public void logOutboxEventPublished(Long notificationId, String status) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "OUTBOX_EVENT_PUBLISHED");
        event.put("notificationId", notificationId);
        event.put("status", status);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Outbox event published", event);
    }

    public void logDuplicateNotificationAttempt(Long invoiceId, String stage) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "DUPLICATE_NOTIFICATION_ATTEMPTED");
        event.put("invoiceId", invoiceId);
        event.put("stage", stage);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.warn("Audit: Duplicate notification attempted (idempotent retry)", event);
    }

    public void logNotificationFailure(Long invoiceId, String channel, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "NOTIFICATION_FAILURE");
        event.put("invoiceId", invoiceId);
        event.put("channel", channel);
        event.put("reason", reason);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.warn("Audit: Notification failure", event);
    }
}
