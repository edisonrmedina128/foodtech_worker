package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler;

import com.foodtech.kitchen.worker.foodtech_worker.application.usecases.ProcessOutboxUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final ProcessOutboxUseCase processOutboxUseCase;

    @Scheduled(fixedRate = 3000) // Runs every 3 seconds
    public void processOutbox() {
        // log.debug("Running outbox scheduler..."); // Commented out to reduce noise
        processOutboxUseCase.processOutboxEvents();
    }
}
