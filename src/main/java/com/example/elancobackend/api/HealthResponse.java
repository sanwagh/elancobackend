package com.example.elancobackend.api;

import java.time.Instant;

// status: UP (last refresh worked), DEGRADED (last refresh failed, serving older data),
// NO_DATA (nothing ever ingested). upstreamReachable reflects the last ingestion run,
// because we never call upstream while answering a request.
public record HealthResponse(String status, Instant lastSuccessfulIngest, long sightingCount,
                             long rejectedRecordCount, boolean upstreamReachable) {
}
