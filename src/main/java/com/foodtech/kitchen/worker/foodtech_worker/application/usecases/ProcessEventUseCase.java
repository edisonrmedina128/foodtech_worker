package com.foodtech.kitchen.worker.foodtech_worker.application.usecases;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.EventPublisherPort;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessEventUseCase {

    private final EventPublisherPort eventPublisherPort;

    public void processAndPublish(String eventType, String payload) {
        log.info("Processing event of type: {}", eventType);

        FoodEvent event = FoodEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();

        eventPublisherPort.publish(event);
        log.info("Event published successfully: {}", event.getEventId());
    }
}
