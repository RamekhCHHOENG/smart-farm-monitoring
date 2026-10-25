package com.smartfarm.domain;

import jakarta.persistence.*;

/**
 * A person who signs in. Farm accounts belong to one farm and have a farm role (owner, worker, technician).
 * Company accounts belong to the company that sets up farms: no farm, and a company role (ADMIN or SUPPORT).
 * Named AppUser because "user" is a reserved word in SQL.
 */
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

    @ManyToOne(fetch = FetchType.LAZY)
    private Farm farm;

    /** ADMIN or SUPPORT for company accounts; null for farm accounts. */
    private String companyRole;

    protected AppUser() {
    }

    public AppUser(Farm farm, String username, String name, String passwordHash, Role role) {
        this.farm = farm;
        this.username = username;
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static AppUser company(String username, String name, String passwordHash, String companyRole) {
        AppUser user = new AppUser(null, username, name, passwordHash, null);
        user.setCompanyRole(companyRole);
        return user;
    }

    public void setCompanyRole(String companyRole) {
        if (!companyRole.equals("ADMIN") && !companyRole.equals("SUPPORT")) {
            throw new IllegalArgumentException("A company account is ADMIN or SUPPORT");
        }
        this.companyRole = companyRole;
    }

    public boolean isCompany() { return companyRole != null; }
    public String getCompanyRole() { return companyRole; }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void update(String name, Role role) {
        this.name = name;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Farm getFarm() { return farm; }
}
