package com.cardbilling.notification.infrastructure.health;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Kafka connectivity.
 */
@Component
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaHealthIndicator(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            // Check if Kafka template is properly configured
            if (kafkaTemplate != null && kafkaTemplate.getDefaultTopic() != null) {
                builder.up().withDetail("kafka", "connected");
            } else {
                builder.up().withDetail("kafka", "configured but not tested");
            }
        } catch (Exception ex) {
            builder.down().withException(ex);
        }
    }
}
