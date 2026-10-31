package com.example.elancobackend.api;

import com.example.elancobackend.model.IngestionRun;

import java.time.Instant;

public record RunSummary(Long id, String status, Instant startedAt, Instant finishedAt, int fetched,
                         int inserted, int updated, int duplicates, int rejected, String errorMessage) {

    public static RunSummary from(IngestionRun r) {
        return new RunSummary(r.getId(), r.getStatus(), r.getStartedAt(), r.getFinishedAt(), r.getFetched(),
                r.getInserted(), r.getUpdated(), r.getDuplicates(), r.getRejected(), r.getErrorMessage());
    }
}
