package com.example.elancobackend.repository;

// One row of the "sightings per region" query.
public record RegionCount(String region, long count) {
}
