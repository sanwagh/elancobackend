package com.example.elancobackend.ingestion;

import com.example.elancobackend.client.UpstreamSighting;
import com.example.elancobackend.model.Sighting;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

// Turns one raw upstream record into a clean Sighting, or throws InvalidRecordException.
@Component
public class SightingCleaner {

    public Sighting clean(UpstreamSighting raw) {
        String id = tidy(raw.id());
        String region = tidy(raw.location());
        String species = tidy(raw.species());
        String latinName = tidy(raw.latinName());

        if (id.isEmpty()) {
            throw new InvalidRecordException("Missing id");
        }
        if (region.isEmpty()) {
            throw new InvalidRecordException("Missing location");
        }
        if (species.isEmpty()) {
            throw new InvalidRecordException("Missing species");
        }
        if (latinName.isEmpty()) {
            throw new InvalidRecordException("Missing latinName");
        }

        return new Sighting(id, titleCase(region), sentenceCase(species), sentenceCase(latinName),
                parseDate(raw.date()));
    }


    private LocalDateTime parseDate(String raw) {
        String value = tidy(raw);
        if (value.isEmpty()) {
            throw new InvalidRecordException("Missing date");
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through and try a plain date
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new InvalidRecordException("Unparseable date: " + value);
        }
    }

    // Trim and collapse repeated spaces. Null becomes "" .
    private String tidy(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.split(" ")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(sentenceCase(word));
        }
        return result.toString();
    }

    private String sentenceCase(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }
}
