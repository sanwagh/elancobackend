package com.example.elancobackend.repository;

import com.example.elancobackend.model.Sighting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SightingRepository extends JpaRepository<Sighting, String> {

    // Every filter is optional: a null parameter switches that condition off.
    // Region and species match case-insensitively. rangeStart is inclusive, rangeEnd is exclusive
    // (the service passes "midnight after the end date" so the end date itself is included).
    @Query("""
            select s from Sighting s
            where (:region is null or lower(s.region) = lower(:region))
              and (:species is null or lower(s.species) = lower(:species))
              and (:rangeStart is null or s.sightingDate >= :rangeStart)
              and (:rangeEnd is null or s.sightingDate < :rangeEnd)
            """)
    Page<Sighting> search(@Param("region") String region,
                          @Param("species") String species,
                          @Param("rangeStart") LocalDateTime rangeStart,
                          @Param("rangeEnd") LocalDateTime rangeEnd,
                          Pageable pageable);

    // Sightings per region, most first. The database does the counting (GROUP BY).
    @Query("""
            select new com.example.elancobackend.repository.RegionCount(s.region, count(s))
            from Sighting s
            where (:rangeStart is null or s.sightingDate >= :rangeStart)
              and (:rangeEnd is null or s.sightingDate < :rangeEnd)
            group by s.region
            order by count(s) desc, s.region
            """)
    List<RegionCount> countByRegion(@Param("rangeStart") LocalDateTime rangeStart,
                                    @Param("rangeEnd") LocalDateTime rangeEnd);

    // Sightings per calendar day (GROUP BY in the database). The service then groups these
    // days into weeks or months; see StatsService for why.
    @Query("""
            select new com.example.elancobackend.repository.DailyCount(cast(s.sightingDate as LocalDate), count(s))
            from Sighting s
            where (:region is null or lower(s.region) = lower(:region))
              and (:rangeStart is null or s.sightingDate >= :rangeStart)
              and (:rangeEnd is null or s.sightingDate < :rangeEnd)
            group by cast(s.sightingDate as LocalDate)
            order by cast(s.sightingDate as LocalDate)
            """)
    List<DailyCount> countByDay(@Param("region") String region,
                                @Param("rangeStart") LocalDateTime rangeStart,
                                @Param("rangeEnd") LocalDateTime rangeEnd);
}
