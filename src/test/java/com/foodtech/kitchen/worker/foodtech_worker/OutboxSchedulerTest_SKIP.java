package com.foodtech.kitchen.worker.foodtech_worker;

import com.foodtech.kitchen.worker.foodtech_worker.application.usecases.ProcessOutboxUseCase;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler.OutboxScheduler;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler.SchedulerTracker;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// NOTE: previously had syntax error and single-arg constructor — fixed to match current OutboxScheduler signature.
class OutboxSchedulerLoggingTest {

    @Test
    void shouldInvokeUseCaseAndRecordExecution() {
        ProcessOutboxUseCase useCase = mock(ProcessOutboxUseCase.class);
        SchedulerTracker tracker = mock(SchedulerTracker.class);

        OutboxScheduler scheduler = new OutboxScheduler(useCase, tracker);
        scheduler.processOutbox();

        verify(useCase).processOutboxEvents();
        verify(tracker).recordExecution();
    }
}

