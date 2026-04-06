package com.foodtech.kitchen.worker.foodtech_worker.application.service;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.OutboxRepositoryPort;
import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.SchedulerStatePort;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Computes the health status of the outbox subsystem.
 * Plain Java class — no framework dependencies. Instantiated as a Spring @Bean
 * by {@code HealthConfig} in the infrastructure layer.
 */
public class OutboxHealthMetrics {

    private final OutboxRepositoryPort outboxRepositoryPort;
    private final SchedulerStatePort schedulerStatePort;
    private final int warnThreshold;
    private final int downThreshold;
    private final int schedulerTimeoutSeconds;

    public OutboxHealthMetrics(OutboxRepositoryPort outboxRepositoryPort,
                               SchedulerStatePort schedulerStatePort,
                               int warnThreshold,
                               int downThreshold,
                               int schedulerTimeoutSeconds) {
        this.outboxRepositoryPort = outboxRepositoryPort;
        this.schedulerStatePort = schedulerStatePort;
        this.warnThreshold = warnThreshold;
        this.downThreshold = downThreshold;
        this.schedulerTimeoutSeconds = schedulerTimeoutSeconds;
    }

    public OutboxHealthSnapshot compute() {
        long pendingEvents;
        long failedEvents;

        try {
            pendingEvents = outboxRepositoryPort.countByStatus("NEW");
            failedEvents = outboxRepositoryPort.countByStatus("FAILED");
        } catch (Exception e) {
            return new OutboxHealthSnapshot(0, 0, null, "DOWN",
                    "Could not query outbox metrics: " + e.getMessage());
        }

        Optional<LocalDateTime> lastProcessedAt = schedulerStatePort.getLastProcessedAt();

        // DOWN: critical failed-events threshold
        if (failedEvents >= downThreshold) {
            return new OutboxHealthSnapshot(pendingEvents, failedEvents,
                    lastProcessedAt.orElse(null), "DOWN",
                    "failedEvents critical threshold exceeded: " + failedEvents + " >= " + downThreshold);
        }

        // DOWN: scheduler stall (only when lastProcessedAt is known)
        if (lastProcessedAt.isPresent()) {
            long elapsedSeconds = Duration.between(lastProcessedAt.get(), LocalDateTime.now()).getSeconds();
            if (elapsedSeconds > schedulerTimeoutSeconds) {
                return new OutboxHealthSnapshot(pendingEvents, failedEvents,
                        lastProcessedAt.get(), "DOWN",
                        "Scheduler stalled: last execution " + elapsedSeconds + "s ago (threshold: "
                                + schedulerTimeoutSeconds + "s)");
            }
        }

        // WARN: warn failed-events threshold
        if (failedEvents >= warnThreshold) {
            return new OutboxHealthSnapshot(pendingEvents, failedEvents,
                    lastProcessedAt.orElse(null), "WARN",
                    "failedEvents threshold exceeded: " + failedEvents + " >= " + warnThreshold);
        }

        // UP
        String reason = lastProcessedAt.isEmpty() ? "No events processed since last startup" : null;
        return new OutboxHealthSnapshot(pendingEvents, failedEvents,
                lastProcessedAt.orElse(null), "UP", reason);
    }

    public record OutboxHealthSnapshot(
            long pendingEvents,
            long failedEvents,
            LocalDateTime lastProcessedAt,
            String status,
            String reason) {
    }
}
