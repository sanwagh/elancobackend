package com.example.elancobackend;

import com.example.elancobackend.client.UpstreamClient;
import com.example.elancobackend.client.UpstreamSighting;
import com.example.elancobackend.ingestion.IngestionService;
import com.example.elancobackend.model.IngestionRun;
import com.example.elancobackend.model.RejectedRecord;
import com.example.elancobackend.repository.IngestionRunRepository;
import com.example.elancobackend.repository.RejectedRecordRepository;
import com.example.elancobackend.repository.SightingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// The upstream client is mocked; everything else (cleaner, repositories, H2, transactions) is real.
// Not @Transactional on purpose: the service manages its own transaction, so we must let it commit.
@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceTest {

    @MockitoBean
    UpstreamClient upstreamClient;

    @Autowired
    IngestionService service;
    @Autowired
    SightingRepository sightingRepository;
    @Autowired
    RejectedRecordRepository rejectedRecordRepository;
    @Autowired
    IngestionRunRepository ingestionRunRepository;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        sightingRepository.deleteAll();
        rejectedRecordRepository.deleteAll();
        ingestionRunRepository.deleteAll();
    }

    private UpstreamSighting raw(String id, String location) {
        return new UpstreamSighting(id, "2025-08-14T17:30:00", location, "Marsh tick", "Ixodes apronophorus");
    }

    @Test
    void insertsValidRecordsAndSummarises() {
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(raw("a1", "london"), raw("a2", "Leeds")));

        IngestionRun run = service.runIngestion();

        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        assertThat(run.getFetched()).isEqualTo(2);
        assertThat(run.getInserted()).isEqualTo(2);
        assertThat(sightingRepository.findById("a1").orElseThrow().getRegion()).isEqualTo("London");
    }

    @Test
    void savesInvalidRecordsToRejectedWithRawPayloadAndReason() {
        UpstreamSighting bad = new UpstreamSighting("b1", "not-a-date", "London", "Marsh tick", "Ixodes x");
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(raw("a1", "London"), bad));

        IngestionRun run = service.runIngestion();

        assertThat(run.getInserted()).isEqualTo(1);
        assertThat(run.getRejected()).isEqualTo(1);
        List<RejectedRecord> rejected = rejectedRecordRepository.findAll();
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).getReason()).contains("Unparseable date");
        assertThat(rejected.get(0).getRawPayload()).contains("b1").contains("not-a-date");
        assertThat(rejected.get(0).getRunId()).isEqualTo(run.getId());
    }

    @Test
    void sameIdTwiceInOneFetchIsStoredOnce() {
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(raw("a1", "London"), raw("a1", "London")));

        IngestionRun run = service.runIngestion();

        assertThat(run.getInserted()).isEqualTo(1);
        assertThat(run.getDuplicates()).isEqualTo(1);
        assertThat(sightingRepository.count()).isEqualTo(1);
    }

    @Test
    void rerunningWithSameDataInsertsNothingNew() {
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(raw("a1", "London"), raw("a2", "Leeds")));
        service.runIngestion();

        IngestionRun second = service.runIngestion();

        assertThat(second.getInserted()).isZero();
        assertThat(second.getUpdated()).isZero();
        assertThat(second.getDuplicates()).isEqualTo(2);
        assertThat(sightingRepository.count()).isEqualTo(2);
    }

    @Test
    void changedRecordIsUpdatedNotDuplicated() {
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(raw("a1", "London")));
        service.runIngestion();
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(raw("a1", "Leeds")));

        IngestionRun second = service.runIngestion();

        assertThat(second.getUpdated()).isEqualTo(1);
        assertThat(sightingRepository.count()).isEqualTo(1);
        assertThat(sightingRepository.findById("a1").orElseThrow().getRegion()).isEqualTo("Leeds");
    }

    @Test
    void upstreamFailureKeepsOldDataAndRecordsFailedRun() {
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(raw("a1", "London")));
        service.runIngestion();
        when(upstreamClient.fetchAllSightings()).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        IngestionRun failed = service.runIngestion();

        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorMessage()).contains("HttpServerErrorException");
        assertThat(sightingRepository.count()).isEqualTo(1);
        assertThat(sightingRepository.findById("a1").orElseThrow().getSightingDate())
                .isEqualTo(LocalDateTime.of(2025, 8, 14, 17, 30));
    }

    @Test
    void secondRunWhileOneIsInProgressIsRefused() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(upstreamClient.fetchAllSightings()).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS); // hold the first run open
            return List.of(raw("a1", "London"));
        });

        Thread first = new Thread(service::runIngestion);
        first.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.runIngestion())
                .isInstanceOf(com.example.elancobackend.error.ConflictException.class);

        release.countDown();
        first.join(5000);
        // The guard is released afterwards, so a new run works again.
        assertThat(service.runIngestion().getStatus()).isEqualTo("SUCCESS");
    }
}
