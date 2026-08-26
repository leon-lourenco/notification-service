package com.cardbilling.notification.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares {@code notification.requested} rather than relying on broker-side auto-creation, so the
 * topic exists with a known partition count instead of whatever the broker's default happens to be
 * on the day the first message is published.
 *
 * <p>One partition: ordering of a single aggregate's events is what matters here, and one partition
 * gives it for free without a partitioning strategy to get wrong. Scaling this out would mean
 * partitioning by notification id, which the publisher already sets as the record key.
 */
@Configuration
class NotificationTopics {

    static final String NOTIFICATION_REQUESTED = "notification.requested";

    @Bean
    NewTopic notificationRequestedTopic(
            @Value("${notification.kafka.topic.notification-requested:" + NOTIFICATION_REQUESTED + "}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
