package com.cardbilling.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * The single most important test in this service: the whole loop, end to end, against a real
 * database and a real broker.
 *
 * <p>{@code POST /notifications} → a notification row and an unpublished outbox row committed
 * together → the dispatcher publishes to {@code notification.requested} → this service's own
 * consumer receives it and records the notification {@code SENT}. That chain is what the monolith
 * could not guarantee, and it is asserted here against raw SQL rather than through the
 * application's own repositories, so the evidence is what is actually in the database.
 */
class NotificationOutboxLoopIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("a requested notification is committed with its outbox event, published, consumed and marked SENT")
    void theWholeLoop() {
        long invoiceId = 900_001L;

        EntityExchangeResult<Map<String, Object>> response =
                postNotification(
                        notificationRequestBody(invoiceId, "REMINDER_D5", "maria.silva@example.com"), "test-token");

        // 1. Durably accepted, not "sent" - the response says the request survives from here on,
        //    which is a different and weaker claim than the monolith's implicit one.
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        UUID notificationId = UUID.fromString((String) response.getResponseBody().get("id"));
        assertThat(response.getResponseBody().get("status")).isEqualTo("REQUESTED");

        // 2. Both rows are in the database. The monolith had the first with no guarantee of the
        //    publish that was supposed to follow it; this second row IS that guarantee.
        assertThat(countNotifications(invoiceId)).isEqualTo(1);
        assertThat(outboxEventCountFor(notificationId)).isEqualTo(1);

        // 3. The dispatcher stamps published_at only after the broker acknowledged the record.
        awaitUntil(
                Duration.ofSeconds(30),
                "the outbox event to be published",
                () -> publishedAtFor(notificationId) != null);

        // 4. The consumer read it back off Redpanda and recorded delivery.
        awaitUntil(
                Duration.ofSeconds(30),
                "the notification to reach SENT",
                () -> "SENT".equals(statusOf(notificationId)));

        assertThat(statusOf(notificationId)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select sent_at from notifications where id = ?", Object.class, notificationId))
                .isNotNull();
    }

    @Test
    @DisplayName("no outbox event is left behind unpublished once the loop has run")
    void outboxDrainsCompletely() {
        long invoiceId = 900_002L;
        UUID notificationId =
                UUID.fromString(
                        (String)
                                postNotification(notificationRequestBody(invoiceId, "REMINDER_D15", null), "test-token")
                                        .getResponseBody()
                                        .get("id"));

        awaitUntil(
                Duration.ofSeconds(30),
                "the notification to reach SENT",
                () -> "SENT".equals(statusOf(notificationId)));

        Integer stillPending =
                jdbc.queryForObject(
                        "select count(*) from outbox_events where aggregate_id = ? and published_at is null",
                        Integer.class,
                        notificationId);
        assertThat(stillPending).isZero();
    }

    @Test
    @DisplayName("the endpoint is closed without a token")
    void requiresAToken() {
        EntityExchangeResult<Map<String, Object>> response =
                postNotification(notificationRequestBody(900_003L, "REMINDER_D5", null), null);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(countNotifications(900_003L)).isZero();
    }

    @Test
    @DisplayName("a malformed request is refused as problem+json, not as a stack trace")
    void malformedRequestIsAProblemDetail() {
        var response =
                client()
                        .post()
                        .uri("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> headers.setBearerAuth("test-token"))
                        .body(
                                """
                                {"customerId": 42, "invoiceId": -1, "channel": "EMAIL", "stage": "REMINDER_D5"}""")
                        .exchange()
                        .returnResult(String.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponseHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getResponseBody()).contains("invoiceId").doesNotContain("Exception");
    }

    private Integer countNotifications(long invoiceId) {
        return jdbc.queryForObject(
                "select count(*) from notifications where invoice_id = ?", Integer.class, invoiceId);
    }

    private Integer outboxEventCountFor(UUID notificationId) {
        return jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?", Integer.class, notificationId);
    }

    private Object publishedAtFor(UUID notificationId) {
        return jdbc.queryForObject(
                "select published_at from outbox_events where aggregate_id = ?", Object.class, notificationId);
    }

    private String statusOf(UUID notificationId) {
        return jdbc.queryForObject("select status from notifications where id = ?", String.class, notificationId);
    }
}
