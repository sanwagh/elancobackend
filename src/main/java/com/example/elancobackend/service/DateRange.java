package com.example.elancobackend.service;

import com.example.elancobackend.error.InvalidRequestException;

import java.time.LocalDate;
import java.time.LocalDateTime;

// An optional start/end date filter, converted to the timestamps the database compares against.
// start is inclusive; end is exclusive here because it is "midnight after the end date", so the
// whole end day counts (sightings carry a time, e.g. 2025-03-01T23:59 must match end=2025-03-01).
public record DateRange(LocalDateTime start, LocalDateTime end) {

    public static DateRange of(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new InvalidRequestException("start must be on or before end");
        }
        return new DateRange(start == null ? null : start.atStartOfDay(),
                end == null ? null : end.plusDays(1).atStartOfDay());
    }
}
