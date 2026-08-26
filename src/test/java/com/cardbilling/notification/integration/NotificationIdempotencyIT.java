package com.cardbilling.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * {@code POST /notifications} is idempotent on {@code (invoiceId, stage, channel)}, enforced by a
 * unique constraint rather than by trusting the caller to check first.
 *
 * <p>This matters more than it looks. {@code collections-service} reruns its cycle, retries failed
 * calls, and escalates the same invoice through three stages; without this guarantee a retried run
 * means a customer gets the same overdue notice twice. Making the database the arbiter is what
 * makes the guarantee hold for two callers arriving at once, not only for a polite one.
 *
 * <p>Getting the key too narrow is just as wrong as having none. The monolith notified on both
 * email and SMS at the same escalation stage, so a key of {@code (invoiceId, stage)} would treat
 * the second channel as a retry and drop it while answering {@code 200} - a silent loss, which is
 * the same class of bug this service was built to remove.
 */
class NotificationIdempotencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("requesting the same invoice, stage and channel twice returns the same record and creates one row")
    void secondRequestReturnsTheExistingRecord() {
        long invoiceId = 910_001L;

        EntityExchangeResult<Map<String, Object>> first = post(invoiceId, "REMINDER_D5", "EMAIL");
        EntityExchangeResult<Map<String, Object>> second = post(invoiceId, "REMINDER_D5", "EMAIL");

        assertThat(first.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        // 200, not 202 and not an error: the request was already accepted, nothing new happened,
        // and a legitimate retry is not a failure.
        assertThat(second.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(second.getResponseBody().get("id")).isEqualTo(first.getResponseBody().get("id"));

        assertThat(notificationCount(invoiceId, "REMINDER_D5", "EMAIL")).isEqualTo(1);

        // And critically: one outbox event, so the customer is messaged once rather than once per
        // retry the caller happened to make.
        UUID notificationId = UUID.fromString((String) first.getResponseBody().get("id"));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_events where aggregate_id = ?",
                                Integer.class,
                                notificationId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the same invoice at a later escalation stage is a separate notification")
    void escalationToAnotherStageIsANewNotification() {
        long invoiceId = 910_002L;

        EntityExchangeResult<Map<String, Object>> reminder = post(invoiceId, "REMINDER_D5", "EMAIL");
        EntityExchangeResult<Map<String, Object>> formalNotice = post(invoiceId, "FORMAL_NOTICE_D30", "EMAIL");

        assertThat(formalNotice.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(formalNotice.getResponseBody().get("id")).isNotEqualTo(reminder.getResponseBody().get("id"));
        assertThat(notificationCount(invoiceId, "REMINDER_D5", "EMAIL")).isEqualTo(1);
        assertThat(notificationCount(invoiceId, "FORMAL_NOTICE_D30", "EMAIL")).isEqualTo(1);
    }

    @Test
    @DisplayName("the other channel at the same stage is dispatched, not swallowed as a duplicate")
    void secondChannelAtTheSameStageIsDispatched() {
        long invoiceId = 910_004L;

        EntityExchangeResult<Map<String, Object>> email = post(invoiceId, "REMINDER_D5", "EMAIL");
        EntityExchangeResult<Map<String, Object>> sms = post(invoiceId, "REMINDER_D5", "SMS");

        // 202 and a distinct id. Under a (invoiceId, stage) key this came back 200 with the email's
        // record and the SMS was never written, never published and never sent - the caller had no
        // way to tell, which is what made it worth catching.
        assertThat(sms.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(sms.getResponseBody().get("id")).isNotEqualTo(email.getResponseBody().get("id"));

        assertThat(notificationCount(invoiceId, "REMINDER_D5", "EMAIL")).isEqualTo(1);
        assertThat(notificationCount(invoiceId, "REMINDER_D5", "SMS")).isEqualTo(1);

        // Both reach the dispatcher: two outbox events, so two messages actually go out.
        UUID smsId = UUID.fromString((String) sms.getResponseBody().get("id"));
        UUID emailId = UUID.fromString((String) email.getResponseBody().get("id"));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_events where aggregate_id in (?, ?)",
                                Integer.class,
                                emailId,
                                smsId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("concurrent requests for the same invoice, stage and channel still produce exactly one row")
    void concurrentRequestsProduceOneRow() throws Exception {
        long invoiceId = 910_003L;
        int callers = 8;

        var results = new ConcurrentLinkedQueue<EntityExchangeResult<Map<String, Object>>>();
        var startTogether = new CountDownLatch(1);
        var done = new CountDownLatch(callers);

        try (var pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                pool.submit(
                        () -> {
                            try {
                                startTogether.await();
                                // Same channel on every caller: these have to be genuine
                                // duplicates for the constraint to be what settles them. Varying
                                // the channel would make them legitimately distinct requests and
                                // the test would prove nothing about concurrency.
                                results.add(post(invoiceId, "REMINDER_D15", "EMAIL"));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                done.countDown();
                            }
                        });
            }
            startTogether.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        // The pre-check in the use case cannot cover this: several callers can all read "nothing
        // there" before any of them commits. The unique constraint settles it, and the losers
        // re-read and answer with the winner's record instead of failing.
        assertThat(results).hasSize(callers);
        assertThat(notificationCount(invoiceId, "REMINDER_D15", "EMAIL")).isEqualTo(1);
        assertThat(results)
                .allSatisfy(result -> assertThat(result.getStatus()).isIn(HttpStatus.ACCEPTED, HttpStatus.OK));
        assertThat(results).filteredOn(result -> result.getStatus() == HttpStatus.ACCEPTED).hasSize(1);

        List<Object> distinctIds =
                results.stream().map(result -> result.getResponseBody().get("id")).distinct().toList();
        assertThat(distinctIds).hasSize(1);
    }

    private EntityExchangeResult<Map<String, Object>> post(long invoiceId, String stage, String channel) {
        return postNotification(notificationRequestBody(invoiceId, stage, channel, null), "test-token");
    }

    private Integer notificationCount(long invoiceId, String stage, String channel) {
        return jdbc.queryForObject(
                "select count(*) from notifications where invoice_id = ? and stage = ? and channel = ?",
                Integer.class,
                invoiceId,
                stage,
                channel);
    }
}
