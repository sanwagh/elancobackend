package com.example.elancobackend;

import com.example.elancobackend.client.UpstreamClient;
import com.example.elancobackend.client.UpstreamSighting;
import com.example.elancobackend.repository.IngestionRunRepository;
import com.example.elancobackend.repository.RejectedRecordRepository;
import com.example.elancobackend.repository.SightingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Not @Transactional: the ingestion service commits its own transaction, so we clean up by hand.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @MockitoBean
    UpstreamClient upstreamClient;

    @Autowired
    MockMvc mockMvc;
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

    @Test
    void refreshLoadsDataAndReturnsRunSummary() throws Exception {
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(
                new UpstreamSighting("a1", "2025-08-14T17:30:00", "London", "Marsh tick", "Ixodes x"),
                new UpstreamSighting("a2", "bad-date", "Leeds", "Marsh tick", "Ixodes x")));

        mockMvc.perform(post("/admin/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.fetched").value(2))
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.rejected").value(1));

        // The data endpoints now work, and health shows the rejected record.
        mockMvc.perform(get("/sightings")).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/health"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.rejectedRecordCount").value(1));
    }

    @Test
    void refreshReturns502WhenUpstreamFailsAndKeepsOldData() throws Exception {
        when(upstreamClient.fetchAllSightings()).thenReturn(List.of(
                new UpstreamSighting("a1", "2025-08-14T17:30:00", "London", "Marsh tick", "Ixodes x")));
        mockMvc.perform(post("/admin/refresh")).andExpect(status().isOk());

        when(upstreamClient.fetchAllSightings()).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        mockMvc.perform(post("/admin/refresh"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").exists());
        mockMvc.perform(get("/sightings")).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/health"))
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.upstreamReachable").value(false));
    }
}
