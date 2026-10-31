package com.example.elancobackend.ingestion;

import com.example.elancobackend.client.UpstreamClient;
import com.example.elancobackend.client.UpstreamSighting;
import com.example.elancobackend.error.ConflictException;
import com.example.elancobackend.model.IngestionRun;
import com.example.elancobackend.model.RejectedRecord;
import com.example.elancobackend.model.Sighting;
import com.example.elancobackend.repository.IngestionRunRepository;
import com.example.elancobackend.repository.RejectedRecordRepository;
import com.example.elancobackend.repository.SightingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final UpstreamClient upstreamClient;
    private final SightingCleaner cleaner;
    private final SightingRepository sightingRepository;
    private final RejectedRecordRepository rejectedRecordRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    // Only one run at a time (startup, schedule and /admin/refresh could otherwise overlap).
    private final AtomicBoolean running = new AtomicBoolean(false);

    public IngestionService(UpstreamClient upstreamClient, SightingCleaner cleaner,
                            SightingRepository sightingRepository,
                            RejectedRecordRepository rejectedRecordRepository,
                            IngestionRunRepository ingestionRunRepository,
                            TransactionTemplate transactionTemplate, ObjectMapper objectMapper) {
        this.upstreamClient = upstreamClient;
        this.cleaner = cleaner;
        this.sightingRepository = sightingRepository;
        this.rejectedRecordRepository = rejectedRecordRepository;
        this.ingestionRunRepository = ingestionRunRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    // Never throws for upstream/data problems: the outcome is recorded on the returned run
    // (status SUCCESS or FAILED), so callers like the scheduler and /admin/refresh stay simple.
    // The one exception: if another run is in progress this throws ConflictException.
    public IngestionRun runIngestion() {
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("An ingestion run is already in progress");
        }
        try {
            return doRun();
        } finally {
            running.set(false);
        }
    }

    private IngestionRun doRun() {
        // Saved first so it has an id that rejected records can point at.
        IngestionRun run = ingestionRunRepository.save(IngestionRun.start());
        try {
            // The slow HTTP call happens outside any database transaction.
            List<UpstreamSighting> fetched = upstreamClient.fetchAllSightings();

            // Everything below is one transaction: if anything fails, nothing from this run
            // is stored, so the last good data is never left half-updated.
            transactionTemplate.executeWithoutResult(status -> store(run, fetched));
            log.info("Ingestion run {} finished: fetched={} inserted={} updated={} duplicates={} rejected={}",
                    run.getId(), run.getFetched(), run.getInserted(), run.getUpdated(),
                    run.getDuplicates(), run.getRejected());
        } catch (RuntimeException e) {
            log.error("Ingestion run {} failed", run.getId(), e);
            run.finishFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return ingestionRunRepository.save(run);
    }

    private void store(IngestionRun run, List<UpstreamSighting> fetched) {
        int inserted = 0;
        int updated = 0;
        int duplicates = 0;
        int rejected = 0;

        // Step 1: clean every record. Bad ones are saved to rejected_records, never dropped silently.
        Set<String> seenIds = new HashSet<>();
        List<Sighting> cleaned = new ArrayList<>();
        for (UpstreamSighting raw : fetched) {
            try {
                Sighting sighting = cleaner.clean(raw);
                // Same id twice in one fetch: keep the first, count the rest as duplicates.
                if (seenIds.add(sighting.getId())) {
                    cleaned.add(sighting);
                } else {
                    duplicates++;
                }
            } catch (InvalidRecordException e) {
                rejectedRecordRepository.save(new RejectedRecord(toJson(raw), e.getMessage(), run.getId()));
                rejected++;
            }
        }

        // Step 2: one query for everything we already have, instead of one query per record.
        Map<String, Sighting> existing = sightingRepository.findAllById(seenIds).stream()
                .collect(Collectors.toMap(Sighting::getId, Function.identity()));

        // Step 3: insert new ones, update changed ones. A record we already hold unchanged
        // is counted as a duplicate (nothing new to store).
        for (Sighting sighting : cleaned) {
            Sighting stored = existing.get(sighting.getId());
            if (stored == null) {
                sightingRepository.save(sighting);
                inserted++;
            } else if (stored.sameDataAs(sighting)) {
                duplicates++;
            } else {
                // stored is a managed entity, so changing it is saved automatically on commit.
                stored.setRegion(sighting.getRegion());
                stored.setSpecies(sighting.getSpecies());
                stored.setLatinName(sighting.getLatinName());
                stored.setSightingDate(sighting.getSightingDate());
                updated++;
            }
        }

        run.finishSuccess(fetched.size(), inserted, updated, duplicates, rejected);
    }

    private String toJson(UpstreamSighting raw) {
        return objectMapper.writeValueAsString(raw);
    }
}
