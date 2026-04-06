package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.health;

import com.foodtech.kitchen.worker.foodtech_worker.application.service.OutboxHealthMetrics;
import com.foodtech.kitchen.worker.foodtech_worker.application.service.OutboxHealthMetrics.OutboxHealthSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerHealthIndicator")
class WorkerHealthIndicatorTest {

    @Mock
    private OutboxHealthMetrics outboxHealthMetrics;

    private WorkerHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new WorkerHealthIndicator(outboxHealthMetrics);
    }

    @Test
    @DisplayName("returns UP when snapshot status is UP")
    void health_returnsUp_whenSnapshotIsUp() {
        when(outboxHealthMetrics.compute()).thenReturn(
                new OutboxHealthSnapshot(2, 0, LocalDateTime.now(), "UP", null));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKeys("pendingEvents", "failedEvents", "lastProcessedAt");
        assertThat(health.getDetails()).doesNotContainKey("reason");
    }

    @Test
    @DisplayName("returns WARN status when snapshot status is WARN")
    void health_returnsWarn_whenSnapshotIsWarn() {
        when(outboxHealthMetrics.compute()).thenReturn(
                new OutboxHealthSnapshot(5, 10, LocalDateTime.now(), "WARN",
                        "failedEvents threshold exceeded: 10 >= 10"));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("WARN");
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    @DisplayName("returns DOWN when snapshot status is DOWN")
    void health_returnsDown_whenSnapshotIsDown() {
        when(outboxHealthMetrics.compute()).thenReturn(
                new OutboxHealthSnapshot(50, 20, LocalDateTime.now(), "DOWN",
                        "failedEvents critical threshold exceeded: 20 >= 20"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    @DisplayName("includes reason detail when reason is present")
    void health_includesReason_whenReasonIsNotNull() {
        when(outboxHealthMetrics.compute()).thenReturn(
                new OutboxHealthSnapshot(0, 0, null, "UP", "No events processed since last startup"));

        Health health = indicator.health();

        assertThat(health.getDetails()).containsKey("reason");
        assertThat(health.getDetails().get("reason"))
                .isEqualTo("No events processed since last startup");
        assertThat(health.getDetails().get("lastProcessedAt")).isEqualTo("null");
    }

    @Test
    @DisplayName("includes all three metric details in every response")
    void health_includesAllMetricDetails_always() {
        when(outboxHealthMetrics.compute()).thenReturn(
                new OutboxHealthSnapshot(3, 1, LocalDateTime.now(), "UP", null));

        Health health = indicator.health();

        assertThat(health.getDetails())
                .containsKeys("pendingEvents", "failedEvents", "lastProcessedAt");
    }
}
