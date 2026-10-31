package com.example.elancobackend.api;

import com.example.elancobackend.model.Sighting;

import java.time.LocalDateTime;

// What the API returns for one sighting. A separate type so the database entity is never exposed directly.
public record SightingResponse(String id, String region, String species, String latinName,
                               LocalDateTime sightingDate) {

    public static SightingResponse from(Sighting s) {
        return new SightingResponse(s.getId(), s.getRegion(), s.getSpecies(), s.getLatinName(), s.getSightingDate());
    }
}
