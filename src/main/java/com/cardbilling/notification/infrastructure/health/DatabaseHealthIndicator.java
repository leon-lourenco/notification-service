package com.cardbilling.notification.infrastructure.health;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

/**
 * Health indicator for database connectivity.
 */
@Component
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private final EntityManager entityManager;

    public DatabaseHealthIndicator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            Long count = (Long) entityManager.createQuery("SELECT COUNT(n) FROM Notification n")
                .getSingleResult();

            builder.up()
                .withDetail("database", "PostgreSQL")
                .withDetail("notificationCount", count);
        } catch (Exception ex) {
            builder.down().withException(ex);
        }
    }
}
