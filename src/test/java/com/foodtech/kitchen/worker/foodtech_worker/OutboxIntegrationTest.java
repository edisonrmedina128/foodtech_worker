package com.foodtech.kitchen.worker.foodtech_worker;

import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.entity.OutboxEntity;
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.persistence.repository.JpaOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OutboxIntegrationTest {

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldProcessOutboxEventAndPublishToRabbitMQ() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        OutboxEntity entity = OutboxEntity.builder()
                .id(eventId)
                .aggregateType("ORDER")
                .aggregateId("1001")
                .eventType("ORDER_CREATED")
                .payload("{\"item\":\"Burger\"}")
                .status("NEW")
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        jpaOutboxRepository.save(entity);

        // Act + Assert — await scheduler to process the event (CI can be slow)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEntity updatedEntity = jpaOutboxRepository.findById(eventId).orElseThrow();
            assertThat(updatedEntity.getStatus()).isEqualTo("SENT");
            assertThat(updatedEntity.getSentAt()).isNotNull();
            assertThat(updatedEntity.getAttempts()).isEqualTo(1);
            // verify inside untilAsserted so Mockito check is retried until the scheduler thread flushes
            verify(rabbitTemplate, atLeastOnce()).convertAndSend(
                    eq("foodtech.exchange"), eq("foodtech.routingkey"), any(Object.class));
        });
    }
}
