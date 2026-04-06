package com.foodtech.kitchen.worker.foodtech_worker.application.service;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.OutboxRepositoryPort;
import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.SchedulerStatePort;
import com.foodtech.kitchen.worker.foodtech_worker.application.service.OutboxHealthMetrics.OutboxHealthSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxHealthMetrics")
class OutboxHealthMetricsTest {

    @Mock
    private OutboxRepositoryPort outboxRepositoryPort;

    @Mock
    private SchedulerStatePort schedulerStatePort;

    private OutboxHealthMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new OutboxHealthMetrics(outboxRepositoryPort, schedulerStatePort, 10, 20, 30);
    }

    // ─── US2: Component detail ────────────────────────────────────────────────

    @Nested
    @DisplayName("US2 — Component detail")
    class ComponentDetail {

        @Test
        @DisplayName("returns UP with all metrics when all systems are healthy")
        void compute_returnsUpWithMetrics_whenAllHealthy() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(3L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(0L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.of(LocalDateTime.now()));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("UP");
            assertThat(snapshot.pendingEvents()).isEqualTo(3L);
            assertThat(snapshot.failedEvents()).isEqualTo(0L);
            assertThat(snapshot.lastProcessedAt()).isNotNull();
            assertThat(snapshot.reason()).isNull();
        }

        @Test
        @DisplayName("returns null lastProcessedAt and UP on fresh startup")
        void compute_nullLastProcessedAt_onFreshStartup() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(0L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(0L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.empty());

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("UP");
            assertThat(snapshot.lastProcessedAt()).isNull();
            assertThat(snapshot.reason()).isEqualTo("No events processed since last startup");
        }

        @Test
        @DisplayName("returns DOWN with error message when repository throws")
        void compute_returnsDown_whenRepositoryThrows() {
            when(outboxRepositoryPort.countByStatus(anyString()))
                    .thenThrow(new RuntimeException("DB unavailable"));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("DOWN");
            assertThat(snapshot.reason()).contains("DB unavailable");
        }
    }

    // ─── US3: Threshold alerts ────────────────────────────────────────────────

    @Nested
    @DisplayName("US3 — Threshold alerts")
    class ThresholdAlerts {

        @Test
        @DisplayName("returns WARN when failedEvents equals warnThreshold (boundary inclusive)")
        void compute_returnsWarn_whenFailedEventsEqualsWarnThreshold() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(5L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(10L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.of(LocalDateTime.now()));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("WARN");
            assertThat(snapshot.reason()).contains("10");
        }

        @Test
        @DisplayName("returns DOWN when failedEvents equals downThreshold (boundary inclusive)")
        void compute_returnsDown_whenFailedEventsEqualsDownThreshold() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(5L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(20L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.of(LocalDateTime.now()));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("DOWN");
            assertThat(snapshot.reason()).contains("20");
        }

        @Test
        @DisplayName("returns WARN when failedEvents is one below downThreshold")
        void compute_returnsWarn_whenFailedEventsOneBeforeDownThreshold() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(5L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(19L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.of(LocalDateTime.now()));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("WARN");
        }

        @Test
        @DisplayName("returns UP when failedEvents is one below warnThreshold")
        void compute_returnsUp_whenFailedEventsBelowWarnThreshold() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(2L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(9L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.of(LocalDateTime.now()));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("UP");
        }

        @Test
        @DisplayName("DOWN wins when downThreshold equals warnThreshold (misconfiguration)")
        void compute_downWins_whenMisconfiguredThresholdsAreEqual() {
            OutboxHealthMetrics misconfigured =
                    new OutboxHealthMetrics(outboxRepositoryPort, schedulerStatePort, 10, 10, 30);

            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(0L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(10L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.of(LocalDateTime.now()));

            OutboxHealthSnapshot snapshot = misconfigured.compute();

            assertThat(snapshot.status()).isEqualTo("DOWN");
        }
    }

    // ─── US4: Scheduler stall detection ──────────────────────────────────────

    @Nested
    @DisplayName("US4 — Scheduler stall detection")
    class SchedulerStall {

        @Test
        @DisplayName("returns DOWN when lastProcessedAt exceeds schedulerTimeout")
        void compute_returnsDown_whenSchedulerStalled() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(2L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(0L);
            when(schedulerStatePort.getLastProcessedAt())
                    .thenReturn(Optional.of(LocalDateTime.now().minusSeconds(31)));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("DOWN");
            assertThat(snapshot.reason()).contains("Scheduler stalled");
            assertThat(snapshot.reason()).contains("31");
        }

        @Test
        @DisplayName("returns UP when lastProcessedAt is within the timeout window")
        void compute_returnsUp_whenSchedulerWithinTimeout() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(0L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(0L);
            when(schedulerStatePort.getLastProcessedAt())
                    .thenReturn(Optional.of(LocalDateTime.now().minusSeconds(29)));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("UP");
        }

        @Test
        @DisplayName("returns UP with informational reason when lastProcessedAt is null (fresh startup)")
        void compute_returnsUp_whenLastProcessedAtIsNull() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(0L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(0L);
            when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.empty());

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("UP");
            assertThat(snapshot.reason()).isEqualTo("No events processed since last startup");
        }

        @Test
        @DisplayName("DOWN from scheduler stall wins over failedEvents below warnThreshold")
        void compute_returnsDown_schedulerStallWins_overLowFailedEvents() {
            when(outboxRepositoryPort.countByStatus("NEW")).thenReturn(3L);
            when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(2L);
            when(schedulerStatePort.getLastProcessedAt())
                    .thenReturn(Optional.of(LocalDateTime.now().minusSeconds(35)));

            OutboxHealthSnapshot snapshot = metrics.compute();

            assertThat(snapshot.status()).isEqualTo("DOWN");
            assertThat(snapshot.reason()).contains("Scheduler stalled");
        }
    }
}
