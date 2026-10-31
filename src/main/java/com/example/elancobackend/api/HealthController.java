package com.example.elancobackend.api;

import com.example.elancobackend.model.IngestionRun;
import com.example.elancobackend.repository.IngestionRunRepository;
import com.example.elancobackend.repository.RejectedRecordRepository;
import com.example.elancobackend.repository.SightingRepository;
import com.example.elancobackend.service.DataAvailability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class HealthController {

    private final DataAvailability dataAvailability;
    private final SightingRepository sightingRepository;
    private final RejectedRecordRepository rejectedRecordRepository;
    private final IngestionRunRepository ingestionRunRepository;

    public HealthController(DataAvailability dataAvailability, SightingRepository sightingRepository,
                            RejectedRecordRepository rejectedRecordRepository,
                            IngestionRunRepository ingestionRunRepository) {
        this.dataAvailability = dataAvailability;
        this.sightingRepository = sightingRepository;
        this.rejectedRecordRepository = rejectedRecordRepository;
        this.ingestionRunRepository = ingestionRunRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Instant lastSuccess = ingestionRunRepository.findFirstByStatusOrderByIdDesc("SUCCESS")
                .map(IngestionRun::getFinishedAt)
                .orElse(null);
        boolean upstreamReachable = ingestionRunRepository.findFirstByStatusNotOrderByIdDesc("RUNNING")
                .map(run -> run.getStatus().equals("SUCCESS"))
                .orElse(false);

        String status;
        if (!dataAvailability.hasEverIngested()) {
            status = "NO_DATA";
        } else if (upstreamReachable) {
            status = "UP";
        } else {
            status = "DEGRADED";
        }

        HealthResponse body = new HealthResponse(status, lastSuccess, sightingRepository.count(),
                rejectedRecordRepository.count(), upstreamReachable);
        // 503 only when we have never had data, matching the rule for the other endpoints.
        HttpStatus http = status.equals("NO_DATA") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(http).body(body);
    }
}
