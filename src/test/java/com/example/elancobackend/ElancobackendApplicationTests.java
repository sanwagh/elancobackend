package com.example.elancobackend;

import com.example.elancobackend.model.Sighting;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ElancobackendApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    @Test
    void contextLoads() {
    }

    @Test
    void swaggerApiDocsAreServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void sightingCanBeSavedAndReadBack() {
        LocalDateTime when = LocalDateTime.of(2025, 8, 14, 17, 30);
        em.persist(new Sighting("abc", "London", "Marsh tick", "Ixodes apronophorus", when));
        em.flush();
        em.clear();

        Sighting found = em.find(Sighting.class, "abc");
        assertThat(found.getRegion()).isEqualTo("London");
        assertThat(found.getSightingDate()).isEqualTo(when);
    }

    @Test
    void duplicateIdIsRejectedByDatabase() {
        LocalDateTime when = LocalDateTime.of(2025, 8, 14, 17, 30);
        em.persist(new Sighting("same-id", "London", "Marsh tick", "Ixodes apronophorus", when));
        em.flush();
        em.clear();

        // persist() on an id that already exists in the database fails on flush.
        assertThatThrownBy(() -> {
            em.persist(new Sighting("same-id", "Leeds", "Marsh tick", "Ixodes apronophorus", when));
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }
}
