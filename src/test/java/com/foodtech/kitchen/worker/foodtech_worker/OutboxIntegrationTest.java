package com.foodtech.kitchen.worker.foodtech_worker;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.EventPublisherPort;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler.OutboxScheduler;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.entity.OutboxEntity;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.repository.JpaOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OutboxIntegrationTest {

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @Autowired
    private OutboxScheduler outboxScheduler;

    // Mock at the hexagonal port level — direct dependency of ProcessOutboxUseCase,
    // guaranteed to be invoked regardless of AMQP infrastructure wiring in any environment.
    @MockitoBean
    private EventPublisherPort eventPublisherPort;

    @Test
    void shouldProcessOutboxEventAndPublishToRabbitMQ() {
        UUID eventId = UUID.randomUUID();
        jpaOutboxRepository.save(OutboxEntity.builder()
                .id(eventId)
                .aggregateType("ORDER")
                .aggregateId("1001")
                .eventType("ORDER_CREATED")
                .payload("{\"item\":\"Burger\"}")
                .status("NEW")
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build());

        // Invoke the scheduler directly — synchronous, deterministic, no timing dependencies
        outboxScheduler.processOutbox();

        OutboxEntity result = jpaOutboxRepository.findById(eventId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getAttempts()).isEqualTo(1);
        verify(eventPublisherPort, times(1)).publish(any(FoodEvent.class));
    }
}
