package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "foodtech.health")
public class HealthThresholdConfig {
    private int warnThreshold = 10;
    private int downThreshold = 20;
    private int schedulerTimeoutSeconds = 30;
}
