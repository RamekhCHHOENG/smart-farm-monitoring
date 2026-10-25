package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A one-time link that lets a new person set their own password (a farm owner the company just created, or new
 * company staff). Only the hash of the link's token is stored; the link works once and expires.
 */
@Entity
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String tokenHash;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser user;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;

    protected Invite() {
    }

    public Invite(AppUser user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean usableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        usedAt = now;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
