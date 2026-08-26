package com.cardbilling.notification.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres, real broker. Nothing about the outbox is provable against an in-memory database
 * and a mocked producer: the guarantee is a property of a transaction committing against a database
 * and a record being acknowledged by a broker, so both have to be real for the test to mean
 * anything.
 *
 * <p>Redpanda rather than the generic Kafka module, because {@code docker-compose.yml} runs
 * Redpanda - testing against a different broker than the one the service is actually run against
 * would leave a gap exactly where this service's claims live.
 *
 * <p>Containers are started once for the JVM in a static initialiser rather than per test class, so
 * the suite pays the startup cost once and both integration classes share it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // Poll fast: one second is a sensible default for a service that runs for days, but it
            // makes every assertion in this suite wait on it.
            "notification.outbox.poll-interval-ms=200",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Import(TestSecurityConfiguration.class)
abstract class AbstractIntegrationTest {

    static final ParameterizedTypeReference<Map<String, Object>> NOTIFICATION_BODY =
            new ParameterizedTypeReference<>() {};

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @ServiceConnection
    static final RedpandaContainer REDPANDA =
            new RedpandaContainer(DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:latest"));

    static {
        POSTGRES.start();
        REDPANDA.start();
    }

    @LocalServerPort private int port;

    RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /**
     * Posts a notification request. {@code bearerToken} may be null, which is how the suite proves
     * the endpoint is actually closed rather than merely configured to look closed.
     */
    EntityExchangeResult<Map<String, Object>> postNotification(String body, String bearerToken) {
        var request = client().post().uri("/notifications").contentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            request.headers(headers -> headers.setBearerAuth(bearerToken));
        }
        return request.body(body).exchange().returnResult(NOTIFICATION_BODY);
    }

    static String notificationRequestBody(long invoiceId, String stage, String recipient) {
        return notificationRequestBody(invoiceId, stage, "EMAIL", recipient);
    }

    static String notificationRequestBody(long invoiceId, String stage, String channel, String recipient) {
        String recipientField = recipient == null ? "" : ", \"recipient\": \"%s\"".formatted(recipient);
        return """
                {"customerId": 42, "invoiceId": %d, "channel": "%s", "stage": "%s"%s}"""
                .formatted(invoiceId, channel, stage, recipientField);
    }

    /**
     * Polls until {@code condition} holds or the deadline passes.
     *
     * <p>The outbox is asynchronous by construction, so a test asserting immediately after the POST
     * would only be asserting that dispatch had not happened yet. Written by hand rather than
     * pulling in Awaitility for one helper.
     */
    static void awaitUntil(Duration timeout, String description, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description, e);
            }
        }
        throw new AssertionError("Timed out after " + timeout + " waiting for " + description);
    }
}
