package com.example.elancobackend.model;

import jakarta.persistence.*;

import java.time.Instant;

// A raw upstream record that failed validation. Kept (never silently dropped) so it can be reviewed.
@Entity
@Table(name = "rejected_records")
public class RejectedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The original upstream JSON, stored as-is.
    @Lob
    @Column(nullable = false)
    private String rawPayload;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant rejectedAt;

    // The ingestion run that produced this rejection.
    private Long runId;

    protected RejectedRecord() {
        // required by JPA
    }

    public RejectedRecord(String rawPayload, String reason, Long runId) {
        this.rawPayload = rawPayload;
        this.reason = reason;
        this.runId = runId;
        this.rejectedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRawPayload() { return rawPayload; }
    public String getReason() { return reason; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Long getRunId() { return runId; }
}
