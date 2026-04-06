package com.foodtech.kitchen.worker.foodtech_worker.application.ports.output;

import com.foodtech.kitchen.worker.foodtech_worker.domain.model.OutboxEvent;

import java.util.List;

public interface OutboxRepositoryPort {
    List<OutboxEvent> findPendingEvents(int limit);
    void save(OutboxEvent event);
    long countByStatus(String status);
}
