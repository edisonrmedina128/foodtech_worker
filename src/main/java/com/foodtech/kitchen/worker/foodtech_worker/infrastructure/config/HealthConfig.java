package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.config;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.OutboxRepositoryPort;
import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.SchedulerStatePort;
import com.foodtech.kitchen.worker.foodtech_worker.application.service.OutboxHealthMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(HealthThresholdConfig.class)
public class HealthConfig {

    @Bean
    public OutboxHealthMetrics outboxHealthMetrics(OutboxRepositoryPort outboxRepositoryPort,
                                                   SchedulerStatePort schedulerStatePort,
                                                   HealthThresholdConfig config) {
        int warnThreshold = config.getWarnThreshold();
        int downThreshold = config.getDownThreshold();

        if (downThreshold <= warnThreshold) {
            log.warn("[HealthConfig] Misconfiguration: down-threshold ({}) must be > warn-threshold ({})."
                    + " Adjusting down-threshold to {}", downThreshold, warnThreshold, warnThreshold + 1);
            downThreshold = warnThreshold + 1;
        }

        log.info("[HealthConfig] OutboxHealthMetrics configured — warn={}, down={}, schedulerTimeout={}s",
                warnThreshold, downThreshold, config.getSchedulerTimeoutSeconds());

        return new OutboxHealthMetrics(outboxRepositoryPort, schedulerStatePort,
                warnThreshold, downThreshold, config.getSchedulerTimeoutSeconds());
    }
}
