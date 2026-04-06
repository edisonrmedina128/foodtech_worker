package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.SchedulerStatePort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SchedulerTracker implements SchedulerStatePort {

    private final AtomicReference<LocalDateTime> lastProcessedAt = new AtomicReference<>();

    public void recordExecution() {
        lastProcessedAt.set(LocalDateTime.now());
    }

    @Override
    public Optional<LocalDateTime> getLastProcessedAt() {
        return Optional.ofNullable(lastProcessedAt.get());
    }
}
