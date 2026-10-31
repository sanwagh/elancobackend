package com.example.elancobackend.service;

import com.example.elancobackend.error.ServiceUnavailableException;
import com.example.elancobackend.repository.IngestionRunRepository;
import com.example.elancobackend.repository.SightingRepository;
import org.springframework.stereotype.Component;

// Answers "have we ever managed to load data?". Until then the data endpoints return 503
// (instead of a misleading empty 200). Once data exists we keep serving it even if upstream fails.
@Component
public class DataAvailability {

    private final SightingRepository sightingRepository;
    private final IngestionRunRepository ingestionRunRepository;

    public DataAvailability(SightingRepository sightingRepository, IngestionRunRepository ingestionRunRepository) {
        this.sightingRepository = sightingRepository;
        this.ingestionRunRepository = ingestionRunRepository;
    }

    // A successful run that fetched zero records still counts: upstream really was empty.
    public boolean hasEverIngested() {
        return ingestionRunRepository.existsByStatus("SUCCESS") || sightingRepository.count() > 0;
    }

    public void requireData() {
        if (!hasEverIngested()) {
            throw new ServiceUnavailableException("No data has been ingested yet, please try again shortly");
        }
    }
}
