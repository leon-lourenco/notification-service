package com.cardbilling.notification.domain;

/** Base type for everything this service's domain refuses to do, so the web layer can map one hierarchy. */
public abstract class NotificationDomainException extends RuntimeException {

    protected NotificationDomainException(String message) {
        super(message);
    }

    protected NotificationDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
