package com.example.elancobackend;

import com.example.elancobackend.model.Sighting;
import com.example.elancobackend.repository.SightingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatsControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SightingRepository sightingRepository;

    // Dates chosen to hit the tricky cases:
    //  - 2024-12-30 (Mon) and 2025-01-01 (Wed) are in the same week but different months and years
    //  - 2025-01-05 (Sun) is the LAST day of that week; 2025-01-06 (Mon) starts the next one
    @BeforeEach
    void seed() {
        sightingRepository.saveAll(List.of(
                sighting("s1", "London", LocalDateTime.of(2024, 12, 30, 10, 0)),
                sighting("s2", "London", LocalDateTime.of(2025, 1, 1, 10, 0)),
                sighting("s3", "Leeds", LocalDateTime.of(2025, 1, 5, 23, 59)),
                sighting("s4", "London", LocalDateTime.of(2025, 1, 6, 0, 0)),
                sighting("s5", "Leeds", LocalDateTime.of(2025, 1, 6, 8, 0))));
    }

    private Sighting sighting(String id, String region, LocalDateTime when) {
        return new Sighting(id, region, "Marsh tick", "Ixodes x", when);
    }

    @Test
    void countsPerRegionMostFirst() throws Exception {
        mockMvc.perform(get("/stats/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].region").value("London"))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[1].region").value("Leeds"))
                .andExpect(jsonPath("$[1].count").value(2));
    }

    @Test
    void regionCountsRespectDateRange() throws Exception {
        mockMvc.perform(get("/stats/regions").param("start", "2025-01-05").param("end", "2025-01-05"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].region").value("Leeds"))
                .andExpect(jsonPath("$[0].count").value(1));
    }

    @Test
    void weeklyTrendGroupsByMondayAcrossYearBoundary() throws Exception {
        mockMvc.perform(get("/stats/trends").param("interval", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].period").value("2024-12-30"))
                .andExpect(jsonPath("$[0].count").value(3)) // s1, s2, s3 (Sunday night is still that week)
                .andExpect(jsonPath("$[1].period").value("2025-01-06"))
                .andExpect(jsonPath("$[1].count").value(2));
    }

    @Test
    void monthlyTrend() throws Exception {
        mockMvc.perform(get("/stats/trends").param("interval", "month"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].period").value("2024-12"))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[1].period").value("2025-01"))
                .andExpect(jsonPath("$[1].count").value(4));
    }

    @Test
    void intervalDefaultsToMonthAndIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/stats/trends"))
                .andExpect(jsonPath("$[0].period").value("2024-12"));
        mockMvc.perform(get("/stats/trends").param("interval", "WEEK"))
                .andExpect(jsonPath("$[0].period").value("2024-12-30"));
    }

    @Test
    void trendCanBeFilteredByRegionAndDates() throws Exception {
        mockMvc.perform(get("/stats/trends").param("interval", "month").param("region", "london"))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[1].count").value(2));
        mockMvc.perform(get("/stats/trends").param("interval", "month").param("start", "2025-01-01"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].count").value(4));
    }

    @Test
    void noMatchingDataGivesEmptyList() throws Exception {
        mockMvc.perform(get("/stats/trends").param("region", "Atlantis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/stats/regions").param("start", "2030-01-01"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidInputIsBadRequest() throws Exception {
        mockMvc.perform(get("/stats/trends").param("interval", "day"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("interval must be 'week' or 'month'"));
        mockMvc.perform(get("/stats/trends").param("start", "2025-02-01").param("end", "2025-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/stats/regions").param("start", "2025-02-01").param("end", "2025-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/stats/regions").param("end", "nope"))
                .andExpect(status().isBadRequest());
    }
}
