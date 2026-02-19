package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.output.rabbitmq;

import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMqEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitMqEventPublisher rabbitMqEventPublisher;

    @BeforeEach
    void setUp() {
        // Inject values manually since we are not loading Spring Context
        ReflectionTestUtils.setField(rabbitMqEventPublisher, "exchange", "test.exchange");
        ReflectionTestUtils.setField(rabbitMqEventPublisher, "routingKey", "test.routingKey");
    }

    @Test
    void publish_ShouldSendEventToRabbitMq() {
        // Arrange
        FoodEvent event = FoodEvent.builder()
                .eventId("123")
                .eventType("TEST")
                .payload("data")
                .build();

        // Act
        rabbitMqEventPublisher.publish(event);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("test.routingKey"), eq(event));
    }
}
