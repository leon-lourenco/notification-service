package com.cardbilling.notification.infrastructure.persistence;

import com.cardbilling.notification.domain.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    Optional<NotificationEntity> findByInvoiceIdAndStage(long invoiceId, Notification.Stage stage);
}
