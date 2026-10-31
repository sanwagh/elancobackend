package com.example.elancobackend;

import com.example.elancobackend.model.IngestionRun;
import com.example.elancobackend.model.Sighting;
import com.example.elancobackend.repository.IngestionRunRepository;
import com.example.elancobackend.repository.SightingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ErrorHandlingTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SightingRepository sightingRepository;
    @Autowired
    IngestionRunRepository ingestionRunRepository;

    // The "unexpected error" case needs a mocked service, so it lives in UnexpectedErrorTest.

    @Test
    void dataEndpointsReturn503WhenNothingEverIngested() throws Exception {
        mockMvc.perform(get("/sightings"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
        mockMvc.perform(get("/sightings/abc")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/stats/regions")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/stats/trends")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void emptyButSuccessfulIngestIsA200NotA503() throws Exception {
        IngestionRun run = IngestionRun.start();
        run.finishSuccess(0, 0, 0, 0, 0);
        ingestionRunRepository.save(run);

        mockMvc.perform(get("/sightings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unknownUrlUsesOurErrorShape() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/does-not-exist"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void wrongHttpMethodIs405InOurErrorShape() throws Exception {
        mockMvc.perform(post("/sightings"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void healthReportsNoDataThenUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("NO_DATA"))
                .andExpect(jsonPath("$.sightingCount").value(0))
                .andExpect(jsonPath("$.upstreamReachable").value(false));

        sightingRepository.save(new Sighting("s1", "London", "Marsh tick", "Ixodes x", LocalDateTime.now()));
        IngestionRun ok = IngestionRun.start();
        ok.finishSuccess(1, 1, 0, 0, 0);
        ingestionRunRepository.save(ok);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.sightingCount").value(1))
                .andExpect(jsonPath("$.upstreamReachable").value(true))
                .andExpect(jsonPath("$.lastSuccessfulIngest").exists());
    }

    @Test
    void healthIsDegradedButStillServesWhenLatestRunFailed() throws Exception {
        sightingRepository.save(new Sighting("s1", "London", "Marsh tick", "Ixodes x", LocalDateTime.now()));
        IngestionRun ok = IngestionRun.start();
        ok.finishSuccess(1, 1, 0, 0, 0);
        ingestionRunRepository.save(ok);
        IngestionRun failed = IngestionRun.start();
        failed.finishFailed("upstream down");
        ingestionRunRepository.save(failed);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.upstreamReachable").value(false))
                .andExpect(jsonPath("$.lastSuccessfulIngest").exists());
        // ...and the data is still served.
        mockMvc.perform(get("/sightings")).andExpect(status().isOk());
    }
}
