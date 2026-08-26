package com.cardbilling.notification;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification dispatch for {@code card-billing-modernization}.
 *
 * <p>{@code @EnableScheduling} is what makes the outbox dispatcher run; without it the outbox fills
 * and nothing drains it, which would fail closed - requests durable but undelivered - rather than
 * losing them the way the monolith did.
 */
@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
        info =
                @Info(
                        title = "notification-service",
                        version = "0.1.0",
                        description =
                                "Notification dispatch with a transactional outbox. POST /notifications writes "
                                        + "the notification and its outbox event in one local transaction; a poller "
                                        + "publishes to Kafka and this service's own consumer simulates delivery."))
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
