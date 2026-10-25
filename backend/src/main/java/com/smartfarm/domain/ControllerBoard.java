package com.smartfarm.domain;

import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * A controller board on the farm (an ESP32 node) that carries several devices. Each board has its own key,
 * so a lost or stolen board only exposes its own devices, and its key can be replaced on its own.
 * Only a SHA-256 hash of the key is stored; the key itself is shown once, when it is made.
 */
@Entity
@Table(name = "controller_board")
public class ControllerBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private DeviceModel model;

    @Column(length = 64, unique = true)
    private String keyHash;

    /** The key's last 4 characters, so staff can tell which key a board has. */
    @Column(length = 8)
    private String keyHint;

    @Column(length = 500)
    private String notes;

    private Instant lastSeenAt;

    /** The farm the board is installed on; older boards are filled in on start. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Farm farm;

    protected ControllerBoard() {
    }

    public ControllerBoard(String name, DeviceModel model, String notes) {
        update(name, model, notes);
    }

    public void update(String name, DeviceModel model, String notes) {
        if (model != null && !model.getCategory().equals("CONTROLLER")) {
            throw new IllegalArgumentException(model.getName() + " is not a controller board");
        }
        this.name = name;
        this.model = model;
        this.notes = notes;
    }

    public Farm getFarm() { return farm; }
    public void setFarm(Farm farm) { this.farm = farm; }

    /** Makes a new random key, keeps only its hash, and returns the key (shown to staff once). */
    public String newKey() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        String key = "board-" + HexFormat.of().formatHex(bytes);
        keyHash = hash(key);
        keyHint = key.substring(key.length() - 4);
        return key;
    }

    public static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void seen(Instant at) {
        lastSeenAt = at;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public DeviceModel getModel() { return model; }
    public String getKeyHint() { return keyHint; }
    public String getNotes() { return notes; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
