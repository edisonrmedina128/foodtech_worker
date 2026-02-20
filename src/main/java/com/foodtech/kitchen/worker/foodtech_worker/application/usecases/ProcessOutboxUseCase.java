package com.foodtech.kitchen.worker.foodtech_worker.application.usecases;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.EventPublisherPort;
import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.OutboxRepositoryPort;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${foodtech.outbox.max-attempts:3}")
    private int maxAttempts;

    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepositoryPort.findPendingEvents(10); // Batch size 10

        if (pendingEvents.isEmpty()) {
            log.trace("[Outbox] No pending events found");
            return;
        }

        log.info("[Outbox] Found {} pending events to process", pendingEvents.size());

        for (OutboxEvent outboxEvent : pendingEvents) {
            String eventId = outboxEvent.getId().toString();
            try {
                log.info("[Outbox] Processing Event ID: {} | Type: {} | Attempts: {}", 
                        eventId, outboxEvent.getEventType(), outboxEvent.getAttempts());

                // Map OutboxEvent to Domain Event
                FoodEvent foodEvent = FoodEvent.builder()
                        .eventId(eventId)
                        .eventType(outboxEvent.getEventType())
                        .payload(outboxEvent.getPayload())
                        .timestamp(outboxEvent.getCreatedAt())
                        .build();

                log.debug("[Outbox] Payload for Event ID {}: {}", eventId, outboxEvent.getPayload());

                // Publish to RabbitMQ
                eventPublisherPort.publish(foodEvent);
                log.info("[Outbox] Successfully published Event ID: {} to RabbitMQ", eventId);

                // Update success status
                outboxEvent.setStatus("SENT");
                outboxEvent.setSentAt(LocalDateTime.now());
                outboxEvent.setAttempts(outboxEvent.getAttempts() + 1);
                outboxRepositoryPort.save(outboxEvent);

                log.info("[Outbox] Event ID: {} mark as SENT locally", eventId);

            } catch (Exception e) {
                log.error("[Outbox] Failed to process Event ID: {} | Error: {}", eventId, e.getMessage(), e);
                
                // Update failure status
                outboxEvent.setAttempts(outboxEvent.getAttempts() + 1);
                outboxEvent.setLastError(e.getMessage());
                
                if (outboxEvent.getAttempts() >= maxAttempts) {
                     log.warn("[Outbox] Event ID: {} reached max attempts ({}). Marking as FAILED", eventId, maxAttempts);
                     outboxEvent.setStatus("FAILED");
                } else {
                    log.info("[Outbox] Event ID: {} retrying later. Current Attempts: {}", eventId, outboxEvent.getAttempts());
                }
                
                outboxRepositoryPort.save(outboxEvent);
            }
        }
    }
}
