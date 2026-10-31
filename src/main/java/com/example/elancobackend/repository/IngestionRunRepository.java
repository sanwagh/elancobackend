package com.example.elancobackend.repository;

import com.example.elancobackend.model.IngestionRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    boolean existsByStatus(String status);

    Optional<IngestionRun> findFirstByStatusOrderByIdDesc(String status);

    // The most recent run that has finished (i.e. is not still RUNNING), successful or not.
    Optional<IngestionRun> findFirstByStatusNotOrderByIdDesc(String status);
}
