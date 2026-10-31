package com.example.elancobackend.repository;

import com.example.elancobackend.model.RejectedRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectedRecordRepository extends JpaRepository<RejectedRecord, Long> {
}
