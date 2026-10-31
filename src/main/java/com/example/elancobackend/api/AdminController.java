package com.example.elancobackend.api;

import com.example.elancobackend.ingestion.IngestionService;
import com.example.elancobackend.model.IngestionRun;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private final IngestionService ingestionService;

    public AdminController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    // Runs a refresh now and waits for it to finish. 200 with the summary if it worked,
    // 502 (with the same summary, status FAILED) if upstream could not be read.
    // 409 if a refresh is already running (handled by GlobalExceptionHandler).
    @PostMapping("/admin/refresh")
    public ResponseEntity<RunSummary> refresh() {
        IngestionRun run = ingestionService.runIngestion();
        HttpStatus http = run.getStatus().equals("SUCCESS") ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(http).body(RunSummary.from(run));
    }
}
