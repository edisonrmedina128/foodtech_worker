package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerTracker")
class SchedulerTrackerTest {

    private SchedulerTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SchedulerTracker();
    }

    @Test
    @DisplayName("returns empty Optional before first recordExecution call")
    void getLastProcessedAt_returnsEmpty_beforeFirstCall() {
        assertThat(tracker.getLastProcessedAt()).isEmpty();
    }

    @Test
    @DisplayName("returns non-null timestamp after recordExecution")
    void recordExecution_setsLastProcessedAt() {
        LocalDateTime before = LocalDateTime.now();
        tracker.recordExecution();
        LocalDateTime after = LocalDateTime.now();

        assertThat(tracker.getLastProcessedAt()).isPresent();
        assertThat(tracker.getLastProcessedAt().get())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("returns the latest timestamp when called twice")
    void recordExecution_calledTwice_returnsLatestTimestamp() throws InterruptedException {
        tracker.recordExecution();
        LocalDateTime first = tracker.getLastProcessedAt().get();

        Thread.sleep(10);
        tracker.recordExecution();
        LocalDateTime second = tracker.getLastProcessedAt().get();

        assertThat(second).isAfter(first);
    }
}
