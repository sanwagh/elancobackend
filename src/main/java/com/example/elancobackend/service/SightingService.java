package com.example.elancobackend.service;

import com.example.elancobackend.error.InvalidRequestException;
import com.example.elancobackend.error.ResourceNotFoundException;
import com.example.elancobackend.model.Sighting;
import com.example.elancobackend.repository.SightingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SightingService {

    static final int MAX_PAGE_SIZE = 200;

    private final SightingRepository sightingRepository;
    private final DataAvailability dataAvailability;

    public SightingService(SightingRepository sightingRepository, DataAvailability dataAvailability) {
        this.sightingRepository = sightingRepository;
        this.dataAvailability = dataAvailability;
    }

    public Page<Sighting> search(String region, String species, LocalDate start, LocalDate end,
                                 int page, int size) {
        dataAvailability.requireData();
        DateRange range = DateRange.of(start, end);
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // Newest first. The id is a tie-breaker so pages never overlap or skip rows.
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sightingDate").and(Sort.by("id")));
        return sightingRepository.search(blankToNull(region), blankToNull(species), range.start(), range.end(), pageable);
    }

    public Sighting getById(String id) {
        dataAvailability.requireData();
        return sightingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No sighting with id " + id));
    }

    // "?region=" (empty) should mean "no filter", not "region equals empty string".
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
