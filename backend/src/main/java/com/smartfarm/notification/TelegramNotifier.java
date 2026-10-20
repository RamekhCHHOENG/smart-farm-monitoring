package com.smartfarm.notification;

import com.smartfarm.domain.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** Sends alerts to a Telegram chat through the Telegram Bot API. */
public class TelegramNotifier implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final RestClient client;
    private final String chatId;

    public TelegramNotifier(RestClient.Builder builder, String botToken, String chatId) {
        this.client = builder.baseUrl("https://api.telegram.org/bot" + botToken).build();
        this.chatId = chatId;
    }

    @Override
    public boolean send(Alert alert) {
        try {
            client.post()
                    .uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", "[" + alert.getSeverity() + "] " + alert.getMessage()))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            // A failed delivery must never stop the farm automation; the alert stays visible in the app.
            log.error("Telegram delivery failed: {}", e.getMessage());
            return false;
        }
    }
}
