package com.cardbilling.notification.infrastructure.persistence;

import com.cardbilling.notification.application.port.NotificationRequestWriter;
import com.cardbilling.notification.domain.DuplicateNotificationException;
import com.cardbilling.notification.domain.Notification;
import com.cardbilling.notification.domain.OutboxEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of the outbox pattern: one {@code @Transactional} method, two inserts,
 * one commit.
 *
 * <p>Whatever else is true of this service, this method is the fix. The monolith's equivalent path
 * was a save followed by an unrelated {@code kafkaTemplate.send(...)}; here there is no broker call
 * on the request path at all, so there is nothing that can half-succeed. Either the database has
 * both rows or it has neither.
 *
 * <p>Written through {@link EntityManager#persist} rather than a Spring Data {@code save}: the
 * notification's id is assigned in the domain, so {@code save} would treat the object as
 * potentially-existing and issue a {@code SELECT} before every insert. {@code persist} states the
 * intent - this row is new - and skips the round trip.
 */
@Repository
class TransactionalNotificationRequestWriter implements NotificationRequestWriter {

    private final EntityManager entityManager;

    TransactionalNotificationRequestWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Notification writeAtomically(Notification notification, OutboxEvent event) {
        try {
            entityManager.persist(NotificationEntity.fromDomain(notification));
            entityManager.persist(OutboxEventEntity.fromDomain(event));
            // Flushed explicitly so a violated (invoice_id, stage) constraint surfaces here, where
            // it can be translated into a domain concept, rather than at commit time - by which
            // point the caller has already been told the request succeeded.
            entityManager.flush();
        } catch (PersistenceException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new DuplicateNotificationException(
                        notification.getInvoiceId(), notification.getStage(), e);
            }
            throw e;
        }
        return notification;
    }

    /**
     * Hibernate reports a violated constraint as a {@code PersistenceException} wrapping its own
     * {@code ConstraintViolationException}, which in turn wraps the JDBC driver's. The chain is
     * walked by class name rather than by importing Hibernate's type directly so this adapter does
     * not compile against a specific JPA provider's exception hierarchy for one check.
     */
    private static boolean isUniqueConstraintViolation(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (name.endsWith("ConstraintViolationException")
                    || name.equals("java.sql.SQLIntegrityConstraintViolationException")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
