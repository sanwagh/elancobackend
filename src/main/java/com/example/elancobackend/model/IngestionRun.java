package com.example.elancobackend.model;

import jakarta.persistence.*;

import java.time.Instant;

// One row per ingestion attempt. Powers the run summary and the "last successful ingest" in /health.
@Entity
@Table(name = "ingestion_runs")
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    // RUNNING, SUCCESS or FAILED
    @Column(nullable = false)
    private String status;

    private int fetched;
    private int inserted;
    private int updated;
    private int duplicates;
    private int rejected;

    // Set when status is FAILED (e.g. upstream down).
    private String errorMessage;

    protected IngestionRun() {
        // required by JPA
    }

    public static IngestionRun start() {
        IngestionRun run = new IngestionRun();
        run.startedAt = Instant.now();
        run.status = "RUNNING";
        return run;
    }

    public void finishSuccess(int fetched, int inserted, int updated, int duplicates, int rejected) {
        this.fetched = fetched;
        this.inserted = inserted;
        this.updated = updated;
        this.duplicates = duplicates;
        this.rejected = rejected;
        this.status = "SUCCESS";
        this.finishedAt = Instant.now();
    }

    public void finishFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = "FAILED";
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getStatus() { return status; }
    public int getFetched() { return fetched; }
    public int getInserted() { return inserted; }
    public int getUpdated() { return updated; }
    public int getDuplicates() { return duplicates; }
    public int getRejected() { return rejected; }
    public String getErrorMessage() { return errorMessage; }
}
