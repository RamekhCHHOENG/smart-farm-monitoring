package com.smartfarm.notification;

import com.smartfarm.domain.Alert;

/**
 * Abstraction for delivering alerts to farm staff (Dependency Inversion Principle):
 * services depend on this interface, never on Telegram or SMS directly.
 */
public interface NotificationService {

    /** @return true when the alert was delivered */
    boolean send(Alert alert);
}
