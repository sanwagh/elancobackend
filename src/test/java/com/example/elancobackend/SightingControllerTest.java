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
@Transactional // each test's seed data is rolled back afterwards
class SightingControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SightingRepository sightingRepository;

    @BeforeEach
    void seed() {
        sightingRepository.saveAll(List.of(
                sighting("s1", "London", "Marsh tick", LocalDateTime.of(2025, 3, 1, 9, 0)),
                sighting("s2", "London", "Passerine tick", LocalDateTime.of(2025, 3, 1, 23, 59)),
                sighting("s3", "Leeds", "Marsh tick", LocalDateTime.of(2025, 3, 2, 0, 0)),
                sighting("s4", "Leeds", "Marsh tick", LocalDateTime.of(2025, 4, 10, 12, 0))));
    }

    private Sighting sighting(String id, String region, String species, LocalDateTime when) {
        return new Sighting(id, region, species, "Ixodes x", when);
    }

    @Test
    void listsEverythingNewestFirstWithPagingInfo() throws Exception {
        mockMvc.perform(get("/sightings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.content[0].id").value("s4"))
                .andExpect(jsonPath("$.content[3].id").value("s1"))
                .andExpect(jsonPath("$.content[3].sightingDate").value("2025-03-01T09:00:00"));
    }

    @Test
    void regionFilterIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/sightings").param("region", "lONDON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void speciesFilterWorks() throws Exception {
        mockMvc.perform(get("/sightings").param("species", "passerine tick"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("s2"));
    }

    @Test
    void endDateIncludesTheWholeDay() throws Exception {
        // s2 is at 23:59 on 2025-03-01, s3 is at 00:00 on 2025-03-02 (outside).
        mockMvc.perform(get("/sightings").param("start", "2025-03-01").param("end", "2025-03-01"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void combinesFilters() throws Exception {
        mockMvc.perform(get("/sightings").param("region", "Leeds").param("start", "2025-04-01"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("s4"));
    }

    @Test
    void emptyFilterValueMeansNoFilter() throws Exception {
        mockMvc.perform(get("/sightings").param("region", ""))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void paginates() throws Exception {
        mockMvc.perform(get("/sightings").param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/sightings").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void startAfterEndIsBadRequest() throws Exception {
        mockMvc.perform(get("/sightings").param("start", "2025-04-01").param("end", "2025-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("start must be on or before end"))
                .andExpect(jsonPath("$.path").value("/sightings"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/sightings").param("start", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'start': yesterday"));
    }

    @Test
    void invalidPagingIsBadRequest() throws Exception {
        mockMvc.perform(get("/sightings").param("size", "201")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/sightings").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/sightings").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/sightings").param("size", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void getsOneSightingById() throws Exception {
        mockMvc.perform(get("/sightings/s3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s3"))
                .andExpect(jsonPath("$.region").value("Leeds"))
                .andExpect(jsonPath("$.latinName").value("Ixodes x"));
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        mockMvc.perform(get("/sightings/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/sightings/nope"));
    }
}
