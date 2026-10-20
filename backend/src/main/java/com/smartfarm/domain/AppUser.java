package com.smartfarm.domain;

import jakarta.persistence.*;

/** A person who works on the farm. Named AppUser because "user" is a reserved word in SQL. */
@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String name;

    /** BCrypt hash; the plain password is never stored. */
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Farm farm;

    protected AppUser() {
    }

    public AppUser(Farm farm, String username, String name, String passwordHash, Role role) {
        this.farm = farm;
        this.username = username;
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Farm getFarm() { return farm; }
}
