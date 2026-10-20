package com.smartfarm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Shared secret that sensor nodes send in the X-Device-Key header.
 * It comes only from the FARM_DEVICE_KEY environment variable; it is never committed.
 * When it is not set, a random key is generated for this run and printed once to the log.
 */
@Component
public class DeviceKey {

    private static final Logger log = LoggerFactory.getLogger(DeviceKey.class);

    private final byte[] key;

    public DeviceKey(@Value("${farm.device-key:}") String configured) {
        String value = configured;
        if (value == null || value.isBlank()) {
            byte[] random = new byte[24];
            new SecureRandom().nextBytes(random);
            value = HexFormat.of().formatHex(random);
            log.warn("FARM_DEVICE_KEY is not set. Generated a temporary device key for this run: {}", value);
        }
        this.key = value.getBytes(StandardCharsets.UTF_8);
    }

    /** Constant-time comparison so the key cannot be guessed by timing. */
    public boolean matches(String candidate) {
        return candidate != null && MessageDigest.isEqual(key, candidate.getBytes(StandardCharsets.UTF_8));
    }
}
