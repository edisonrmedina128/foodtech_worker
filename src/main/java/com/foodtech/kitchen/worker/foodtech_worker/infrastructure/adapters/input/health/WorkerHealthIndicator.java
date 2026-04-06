package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.health;

import com.foodtech.kitchen.worker.foodtech_worker.application.service.OutboxHealthMetrics;
import com.foodtech.kitchen.worker.foodtech_worker.application.service.OutboxHealthMetrics.OutboxHealthSnapshot;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

@Component
public class WorkerHealthIndicator extends AbstractHealthIndicator {

    private final OutboxHealthMetrics outboxHealthMetrics;

    public WorkerHealthIndicator(OutboxHealthMetrics outboxHealthMetrics) {
        super("WorkerOutbox health check failed");
        this.outboxHealthMetrics = outboxHealthMetrics;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        OutboxHealthSnapshot snapshot = outboxHealthMetrics.compute();

        switch (snapshot.status()) {
            case "DOWN" -> builder.down();
            case "WARN" -> builder.status("WARN");
            default -> builder.up();
        }

        builder.withDetail("pendingEvents", snapshot.pendingEvents())
               .withDetail("failedEvents", snapshot.failedEvents())
               .withDetail("lastProcessedAt",
                       snapshot.lastProcessedAt() != null ? snapshot.lastProcessedAt().toString() : "null");

        if (snapshot.reason() != null) {
            builder.withDetail("reason", snapshot.reason());
        }
    }
}
