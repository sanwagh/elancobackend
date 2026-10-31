package com.example.elancobackend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sightings", indexes = {
        @Index(name = "idx_sightings_region_date", columnList = "region, sighting_date")
})
public class Sighting {

    // The upstream id is unique and always present, so we use it as our primary key.
    // That makes dedupe/upsert simple: saving a sighting with an existing id updates it.
    @Id
    private String id;

    // Upstream calls this "location" but it always holds a UK city, so it is our "region".
    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String species;

    @Column(nullable = false)
    private String latinName;

    // Upstream gives a date AND time with no timezone (e.g. 2025-08-14T17:30:00).
    @Column(name = "sighting_date", nullable = false)
    private LocalDateTime sightingDate;

    protected Sighting() {
        // required by JPA
    }

    public Sighting(String id, String region, String species, String latinName, LocalDateTime sightingDate) {
        this.id = id;
        this.region = region;
        this.species = species;
        this.latinName = latinName;
        this.sightingDate = sightingDate;
    }

    // True if every field except the id matches. Used to tell "changed" from "already have it".
    public boolean sameDataAs(Sighting other) {
        return region.equals(other.region)
                && species.equals(other.species)
                && latinName.equals(other.latinName)
                && sightingDate.equals(other.sightingDate);
    }

    public String getId() { return id; }
    public String getRegion() { return region; }
    public String getSpecies() { return species; }
    public String getLatinName() { return latinName; }
    public LocalDateTime getSightingDate() { return sightingDate; }

    public void setRegion(String region) { this.region = region; }
    public void setSpecies(String species) { this.species = species; }
    public void setLatinName(String latinName) { this.latinName = latinName; }
    public void setSightingDate(LocalDateTime sightingDate) { this.sightingDate = sightingDate; }
}
