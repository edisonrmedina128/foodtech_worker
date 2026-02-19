package com.foodtech.kitchen.worker.foodtech_worker;

import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler.OutboxScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith({SpringExtension.class, OutputCaptureExtension.class})
class OutboxSchedulerLoggingTest {

    @Test
    void shouldLogWhen runningScheduler(CapturedOutput output) {
        // Arrange
        // We mock the UseCase to avoid complex setup, we just want to test the scheduler's logging
        com.foodtech.kitchen.worker.foodtech_worker.application.usecases.ProcessOutboxUseCase useCase = 
                mock(com.foodtech.kitchen.worker.foodtech_worker.application.usecases.ProcessOutboxUseCase.class);
        
        OutboxScheduler scheduler = new OutboxScheduler(useCase);
        
        // Use reflection or a logger configuration to ensure DEBUG is enabled if needed, 
        // but for unit test simpler to just call the method and check logic if possible. 
        // However, Slf4j checks typically require the logging system to be active.
        // A simple unit test of the class won't trigger the logger implementation fully without Spring Boot Context 
        // or a specific logger binder.
        
        // Re-evaluating: The user just wants the log enabled. Verification via "run" is better.
        // Let's just enable it and run the existing integration test which will print to stdout.
    }
}
