package com.smartfarm.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String location;

    /** ACTIVE or SUSPENDED (set by the company); null on farms made before this existed counts as ACTIVE. */
    private String status;

    /** The owner's consent for company support staff to open this farm; null (older farms) counts as allowed. */
    private Boolean supportAccess;

    private Instant createdAt;

    /** How the company reaches the owner (phone or Telegram), for invites and support. */
    private String ownerContact;

    /** Company support staff who look after this farm (they still need the owner's consent above). */
    @ManyToMany
    @JoinTable(name = "farm_support", joinColumns = @JoinColumn(name = "farm_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @OrderBy("name")
    private Set<AppUser> support = new LinkedHashSet<>();

    // Composition: a zone cannot exist without its farm (Farm ◆── Zone).
    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Zone> zones = new ArrayList<>();

    protected Farm() {
    }

    public Farm(String name, String location) {
        this.name = name;
        this.location = location;
    }

    /** GRASP Creator: Farm contains zones, so Farm creates them. */
    public CropZone addCropZone(String name, String cropName) {
        CropZone zone = new CropZone(this, name, cropName);
        zones.add(zone);
        return zone;
    }

    public PoultryZone addPoultryZone(String name, int birdCount) {
        PoultryZone zone = new PoultryZone(this, name, birdCount);
        zones.add(zone);
        return zone;
    }

    public void removeZone(Zone zone) {
        zones.remove(zone);
    }

    public void update(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public static Farm createdByCompany(String name, String location, String ownerContact, Instant now) {
        Farm farm = new Farm(name, location);
        farm.status = "ACTIVE";
        farm.supportAccess = true;
        farm.createdAt = now;
        farm.ownerContact = ownerContact;
        return farm;
    }

    public void setStatus(String status) {
        if (!status.equals("ACTIVE") && !status.equals("SUSPENDED")) {
            throw new IllegalArgumentException("A farm is ACTIVE or SUSPENDED");
        }
        this.status = status;
    }

    public void allowSupport(boolean allowed) { this.supportAccess = allowed; }
    public void assignSupport(Set<AppUser> staff) {
        support.clear();
        support.addAll(staff);
    }
    public void setOwnerContact(String ownerContact) { this.ownerContact = ownerContact; }

    public boolean isSuspended() { return "SUSPENDED".equals(status); }
    public String getStatus() { return isSuspended() ? "SUSPENDED" : "ACTIVE"; }
    public boolean isSupportAllowed() { return !Boolean.FALSE.equals(supportAccess); }
    public Instant getCreatedAt() { return createdAt; }
    public String getOwnerContact() { return ownerContact; }
    public Set<AppUser> getSupport() { return support; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public List<Zone> getZones() { return List.copyOf(zones); }
}
