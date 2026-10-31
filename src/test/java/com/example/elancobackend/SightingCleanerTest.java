package com.example.elancobackend;

import com.example.elancobackend.client.UpstreamSighting;
import com.example.elancobackend.ingestion.InvalidRecordException;
import com.example.elancobackend.ingestion.SightingCleaner;
import com.example.elancobackend.model.Sighting;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SightingCleanerTest {

    private final SightingCleaner cleaner = new SightingCleaner();

    private UpstreamSighting raw(String id, String date, String location, String species, String latin) {
        return new UpstreamSighting(id, date, location, species, latin);
    }

    @Test
    void cleansAValidRecord() {
        Sighting s = cleaner.clean(raw("a1", "2025-08-14T17:30:00", "London", "Marsh tick", "Ixodes apronophorus"));

        assertThat(s.getId()).isEqualTo("a1");
        assertThat(s.getRegion()).isEqualTo("London");
        assertThat(s.getSightingDate()).isEqualTo(LocalDateTime.of(2025, 8, 14, 17, 30));
    }

    @Test
    void trimsAndNormalisesCasing() {
        Sighting s = cleaner.clean(raw("  a1 ", "2025-08-14T17:30:00", "  lONDON ", "FOX/BADGER   tick", "ixodes  CANISUGA"));

        assertThat(s.getId()).isEqualTo("a1");
        assertThat(s.getRegion()).isEqualTo("London");
        assertThat(s.getSpecies()).isEqualTo("Fox/badger tick");
        assertThat(s.getLatinName()).isEqualTo("Ixodes canisuga");
    }

    @Test
    void titleCasesEachWordOfARegion() {
        Sighting s = cleaner.clean(raw("a1", "2025-08-14T17:30:00", "newcastle upon tyne", "Marsh tick", "Ixodes apronophorus"));

        assertThat(s.getRegion()).isEqualTo("Newcastle Upon Tyne");
    }

    @Test
    void acceptsDateWithoutTimeAsMidnight() {
        Sighting s = cleaner.clean(raw("a1", "2025-08-14", "London", "Marsh tick", "Ixodes apronophorus"));

        assertThat(s.getSightingDate()).isEqualTo(LocalDateTime.of(2025, 8, 14, 0, 0));
    }

    @Test
    void rejectsMissingOrBlankFields() {
        assertRejected(raw(null, "2025-08-14T17:30:00", "London", "Marsh tick", "Ixodes x"), "Missing id");
        assertRejected(raw("a1", "2025-08-14T17:30:00", "   ", "Marsh tick", "Ixodes x"), "Missing location");
        assertRejected(raw("a1", "2025-08-14T17:30:00", "London", "", "Ixodes x"), "Missing species");
        assertRejected(raw("a1", "2025-08-14T17:30:00", "London", "Marsh tick", null), "Missing latinName");
        assertRejected(raw("a1", null, "London", "Marsh tick", "Ixodes x"), "Missing date");
    }

    @Test
    void rejectsUnparseableDate() {
        assertRejected(raw("a1", "14/08/2025", "London", "Marsh tick", "Ixodes x"), "Unparseable date");
        assertRejected(raw("a1", "2025-13-45T00:00:00", "London", "Marsh tick", "Ixodes x"), "Unparseable date");
    }

    private void assertRejected(UpstreamSighting raw, String expectedReason) {
        assertThatThrownBy(() -> cleaner.clean(raw))
                .isInstanceOf(InvalidRecordException.class)
                .hasMessageContaining(expectedReason);
    }
}
