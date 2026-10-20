package com.smartfarm.notification;

import com.smartfarm.domain.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default channel for development: writes alerts to the server log. */
public class ConsoleNotifier implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotifier.class);

    @Override
    public boolean send(Alert alert) {
        log.warn("[ALERT {}] {}", alert.getSeverity(), alert.getMessage());
        return true;
    }
}
