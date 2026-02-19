package com.foodtech.kitchen.worker.foodtech_worker.application.usecases;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.EventPublisherPort;
import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.OutboxRepositoryPort;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessOutboxUseCase {

    private final OutboxRepositoryPort outboxRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepositoryPort.findPendingEvents(10); // Batch size 10

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events", pendingEvents.size());

        for (OutboxEvent outboxEvent : pendingEvents) {
            try {
                // Map OutboxEvent to Domain Event
                FoodEvent foodEvent = FoodEvent.builder()
                        .eventId(outboxEvent.getId().toString())
                        .eventType(outboxEvent.getEventType())
                        .payload(outboxEvent.getPayload())
                        .timestamp(outboxEvent.getCreatedAt())
                        .build();

                log.debug("Processing outbox event: id={}, payload={}", outboxEvent.getId(), outboxEvent.getPayload());

                // Publish to RabbitMQ
                eventPublisherPort.publish(foodEvent);

                // Update success status
                outboxEvent.setStatus("SENT");
                outboxEvent.setSentAt(LocalDateTime.now());
                outboxEvent.setAttempts(outboxEvent.getAttempts() + 1);
                outboxRepositoryPort.save(outboxEvent);

                log.info("Successfully processed outbox event: {}", outboxEvent.getId());

            } catch (Exception e) {
                log.error("Failed to process outbox event: {}", outboxEvent.getId(), e);
                
                // Update failure status
                outboxEvent.setAttempts(outboxEvent.getAttempts() + 1);
                outboxEvent.setLastError(e.getMessage());
                
                if (outboxEvent.getAttempts() >= 3) {
                     outboxEvent.setStatus("FAILED");
                }
                
                outboxRepositoryPort.save(outboxEvent);
            }
        }
    }
}
