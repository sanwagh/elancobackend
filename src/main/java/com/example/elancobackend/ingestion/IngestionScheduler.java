package com.example.elancobackend.ingestion;

import com.example.elancobackend.error.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Loads data once the app has started, then refreshes on a fixed delay.
// Can be switched off with ingestion.scheduler-enabled=false (tests do this so they never call upstream).
@Component
@ConditionalOnProperty(name = "ingestion.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final IngestionService ingestionService;

    public IngestionScheduler(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    // Runs after the web server is up, so the API is already answering while the first load happens.
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refresh();
    }

    // The first scheduled run waits one full interval, because the startup run just happened.
    @Scheduled(fixedDelayString = "${ingestion.refresh-interval}", initialDelayString = "${ingestion.refresh-interval}")
    public void refresh() {
        try {
            ingestionService.runIngestion();
        } catch (ConflictException e) {
            log.info("Skipping scheduled refresh: {}", e.getMessage());
        }
    }
}
