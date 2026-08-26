package com.cardbilling.notification.infrastructure.persistence;

import com.cardbilling.notification.application.port.NotificationRepository;
import com.cardbilling.notification.domain.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adapter mapping {@link NotificationRepository} onto Spring Data JPA. */
@Repository
class JpaNotificationRepository implements NotificationRepository {

    private final NotificationJpaRepository notifications;

    JpaNotificationRepository(NotificationJpaRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return notifications.findById(id).map(NotificationEntity::toDomain);
    }

    @Override
    public Optional<Notification> findByInvoiceIdAndStageAndChannel(
            long invoiceId, Notification.Stage stage, Notification.Channel channel) {
        return notifications
                .findByInvoiceIdAndStageAndChannel(invoiceId, stage, channel)
                .map(NotificationEntity::toDomain);
    }

    @Override
    public Notification save(Notification notification) {
        return notifications.save(NotificationEntity.fromDomain(notification)).toDomain();
    }
}
