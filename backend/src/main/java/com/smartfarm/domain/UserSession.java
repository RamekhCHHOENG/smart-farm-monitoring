package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A signed-in browser. Kept in the database so a server restart does not log everyone out.
 * Only the hash of the bearer token is stored: a copy of the database cannot be used to sign in.
 */
@Entity
@Table(indexes = @Index(columnList = "expiresAt"))
public class UserSession {

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

    protected UserSession() {
    }

    public UserSession(AppUser user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean validAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    public Long getUserId() { return user.getId(); }
    public Instant getExpiresAt() { return expiresAt; }
}
