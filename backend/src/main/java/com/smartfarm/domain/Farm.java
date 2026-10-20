package com.smartfarm.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String location;

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

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public List<Zone> getZones() { return List.copyOf(zones); }
}
