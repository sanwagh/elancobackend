package com.example.elancobackend.repository;

import java.time.LocalDate;

// One row of the "sightings per day" query.
public record DailyCount(LocalDate day, long count) {
}
