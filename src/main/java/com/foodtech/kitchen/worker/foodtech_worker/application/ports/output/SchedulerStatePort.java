package com.foodtech.kitchen.worker.foodtech_worker.application.ports.output;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SchedulerStatePort {
    Optional<LocalDateTime> getLastProcessedAt();
}
