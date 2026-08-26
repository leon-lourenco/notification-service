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
 * {@code POST /notifications} is idempotent on {@code (invoiceId, stage)}, enforced by a unique
 * constraint rather than by trusting the caller to check first.
 *
 * <p>This matters more than it looks. {@code collections-service} reruns its cycle, retries failed
 * calls, and escalates the same invoice through three stages; without this guarantee a retried run
 * means a customer gets the same overdue notice twice. Making the database the arbiter is what
 * makes the guarantee hold for two callers arriving at once, not only for a polite one.
 */
class NotificationIdempotencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("requesting the same invoice and stage twice returns the same record and creates one row")
    void secondRequestReturnsTheExistingRecord() {
        long invoiceId = 910_001L;

        EntityExchangeResult<Map<String, Object>> first = post(invoiceId, "REMINDER_D5");
        EntityExchangeResult<Map<String, Object>> second = post(invoiceId, "REMINDER_D5");

        assertThat(first.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        // 200, not 202 and not an error: the request was already accepted, nothing new happened,
        // and a legitimate retry is not a failure.
        assertThat(second.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(second.getResponseBody().get("id")).isEqualTo(first.getResponseBody().get("id"));

        assertThat(notificationCount(invoiceId, "REMINDER_D5")).isEqualTo(1);

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

        EntityExchangeResult<Map<String, Object>> reminder = post(invoiceId, "REMINDER_D5");
        EntityExchangeResult<Map<String, Object>> formalNotice = post(invoiceId, "FORMAL_NOTICE_D30");

        assertThat(formalNotice.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(formalNotice.getResponseBody().get("id")).isNotEqualTo(reminder.getResponseBody().get("id"));
        assertThat(notificationCount(invoiceId, "REMINDER_D5")).isEqualTo(1);
        assertThat(notificationCount(invoiceId, "FORMAL_NOTICE_D30")).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent requests for the same invoice and stage still produce exactly one row")
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
                                results.add(post(invoiceId, "REMINDER_D15"));
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
        assertThat(notificationCount(invoiceId, "REMINDER_D15")).isEqualTo(1);
        assertThat(results)
                .allSatisfy(result -> assertThat(result.getStatus()).isIn(HttpStatus.ACCEPTED, HttpStatus.OK));
        assertThat(results).filteredOn(result -> result.getStatus() == HttpStatus.ACCEPTED).hasSize(1);

        List<Object> distinctIds =
                results.stream().map(result -> result.getResponseBody().get("id")).distinct().toList();
        assertThat(distinctIds).hasSize(1);
    }

    private EntityExchangeResult<Map<String, Object>> post(long invoiceId, String stage) {
        return postNotification(notificationRequestBody(invoiceId, stage, null), "test-token");
    }

    private Integer notificationCount(long invoiceId, String stage) {
        return jdbc.queryForObject(
                "select count(*) from notifications where invoice_id = ? and stage = ?",
                Integer.class,
                invoiceId,
                stage);
    }
}
