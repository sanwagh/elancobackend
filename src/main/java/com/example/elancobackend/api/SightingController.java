package com.example.elancobackend.api;

import com.example.elancobackend.service.SightingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class SightingController {

    private final SightingService sightingService;

    public SightingController(SightingService sightingService) {
        this.sightingService = sightingService;
    }

    // All filters are optional. start/end are ISO dates (2025-03-01) and both are inclusive.
    @GetMapping("/sightings")
    public PageResponse<SightingResponse> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String species,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(sightingService.search(region, species, start, end, page, size),
                SightingResponse::from);
    }

    @GetMapping("/sightings/{id}")
    public SightingResponse getById(@PathVariable String id) {
        return SightingResponse.from(sightingService.getById(id));
    }
}
