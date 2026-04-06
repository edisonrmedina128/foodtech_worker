package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.adapter;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.OutboxRepositoryPort;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.OutboxEvent;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.entity.OutboxEntity;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.repository.JpaOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRepositoryAdapter implements OutboxRepositoryPort {

    private final JpaOutboxRepository jpaOutboxRepository;

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        List<OutboxEntity> entities = jpaOutboxRepository.findPendingEvents(3, PageRequest.of(0, limit));
        log.debug("Polled {} pending events from database", entities.size());
        return entities
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(OutboxEvent event) {
        jpaOutboxRepository.save(toEntity(event));
    }

    @Override
    public long countByStatus(String status) {
        return jpaOutboxRepository.countByStatus(status);
    }

    private OutboxEvent toDomain(OutboxEntity entity) {
        return OutboxEvent.builder()
                .id(entity.getId())
                .aggregateType(entity.getAggregateType())
                .aggregateId(entity.getAggregateId())
                .eventType(entity.getEventType())
                .payload(entity.getPayload())
                .status(entity.getStatus())
                .attempts(entity.getAttempts())
                .nextRetryAt(entity.getNextRetryAt())
                .createdAt(entity.getCreatedAt())
                .sentAt(entity.getSentAt())
                .lastError(entity.getLastError())
                .build();
    }

    private OutboxEntity toEntity(OutboxEvent domain) {
        return OutboxEntity.builder()
                .id(domain.getId())
                .aggregateType(domain.getAggregateType())
                .aggregateId(domain.getAggregateId())
                .eventType(domain.getEventType())
                .payload(domain.getPayload())
                .status(domain.getStatus())
                .attempts(domain.getAttempts())
                .nextRetryAt(domain.getNextRetryAt())
                .createdAt(domain.getCreatedAt())
                .sentAt(domain.getSentAt())
                .lastError(domain.getLastError())
                .build();
    }
}
