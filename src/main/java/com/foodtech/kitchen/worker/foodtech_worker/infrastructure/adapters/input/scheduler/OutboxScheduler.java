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

    @Scheduled(fixedRateString = "${foodtech.outbox.scheduler-rate:3000}")
    public void processOutbox() {
        log.debug("[Scheduler] Triggering outbox processing cycle at {}", java.time.LocalDateTime.now());
        processOutboxUseCase.processOutboxEvents();
        log.debug("[Scheduler] Outbox processing cycle completed");
    }
}
