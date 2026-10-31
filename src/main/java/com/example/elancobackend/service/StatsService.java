package com.example.elancobackend.service;

import com.example.elancobackend.api.TrendPoint;
import com.example.elancobackend.error.InvalidRequestException;
import com.example.elancobackend.repository.DailyCount;
import com.example.elancobackend.repository.RegionCount;
import com.example.elancobackend.repository.SightingRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class StatsService {

    private final SightingRepository sightingRepository;
    private final DataAvailability dataAvailability;

    public StatsService(SightingRepository sightingRepository, DataAvailability dataAvailability) {
        this.sightingRepository = sightingRepository;
        this.dataAvailability = dataAvailability;
    }

    public List<RegionCount> sightingsPerRegion(LocalDate start, LocalDate end) {
        dataAvailability.requireData();
        DateRange range = DateRange.of(start, end);
        return sightingRepository.countByRegion(range.start(), range.end());
    }

    // The database counts sightings per day; here we add the days up into weeks or months.
    // Doing the week/month grouping in SQL would need database-specific functions, and week
    // numbers (e.g. 30 Dec belonging to week 1 of the next year) are easy to get wrong.
    // The number of rows handled here is at most one per day, never one per sighting.
    public List<TrendPoint> trends(String interval, String region, LocalDate start, LocalDate end) {
        dataAvailability.requireData();
        boolean weekly = parseInterval(interval);
        DateRange range = DateRange.of(start, end);
        String regionFilter = region == null || region.isBlank() ? null : region.trim();

        // TreeMap keeps the periods in date order (labels are ISO, so text order is date order).
        Map<String, Long> countPerPeriod = new TreeMap<>();
        for (DailyCount day : sightingRepository.countByDay(regionFilter, range.start(), range.end())) {
            countPerPeriod.merge(periodLabel(day.day(), weekly), day.count(), Long::sum);
        }
        return countPerPeriod.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey(), e.getValue()))
                .toList();
    }

    // Returns true for "week", false for "month". Defaults to month when not given.
    private boolean parseInterval(String interval) {
        if (interval == null || interval.isBlank() || interval.equalsIgnoreCase("month")) {
            return false;
        }
        if (interval.equalsIgnoreCase("week")) {
            return true;
        }
        throw new InvalidRequestException("interval must be 'week' or 'month'");
    }

    // Week -> the Monday it starts on, e.g. "2025-03-03". Month -> "2025-03".
    private String periodLabel(LocalDate day, boolean weekly) {
        if (weekly) {
            return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
        }
        return YearMonth.from(day).toString();
    }
}
