package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.repository;

import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.entity.OutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaOutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    @Query("SELECT e FROM OutboxEntity e WHERE e.status = 'NEW' OR (e.status = 'FAILED' AND e.attempts < :maxAttempts)")
    List<OutboxEntity> findPendingEvents(int maxAttempts, Pageable pageable);

    long countByStatus(String status);
}
