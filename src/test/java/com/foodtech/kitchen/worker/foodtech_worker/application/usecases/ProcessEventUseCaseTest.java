package com.foodtech.kitchen.worker.foodtech_worker.application.usecases;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.EventPublisherPort;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessEventUseCaseTest {

    @Mock
    private EventPublisherPort eventPublisherPort;

    private ProcessEventUseCase processEventUseCase;

    @BeforeEach
    void setUp() {
        processEventUseCase = new ProcessEventUseCase(eventPublisherPort);
    }

    @Test
    void processAndPublish_ShouldCreateEventAndPublish() {
        // Arrange
        String eventType = "TEST_TYPE";
        String payload = "{\"data\":\"test\"}";

        // Act
        processEventUseCase.processAndPublish(eventType, payload);

        // Assert
        ArgumentCaptor<FoodEvent> eventCaptor = ArgumentCaptor.forClass(FoodEvent.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());

        FoodEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent).isNotNull();
        assertThat(capturedEvent.getEventType()).isEqualTo(eventType);
        assertThat(capturedEvent.getPayload()).isEqualTo(payload);
        assertThat(capturedEvent.getEventId()).isNotNull(); // UUID generated
        assertThat(capturedEvent.getTimestamp()).isNotNull(); // Timestamp generated
    }
}
