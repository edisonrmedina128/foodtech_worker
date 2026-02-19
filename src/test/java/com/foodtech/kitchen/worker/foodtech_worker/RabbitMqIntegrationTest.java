package com.foodtech.kitchen.worker.foodtech_worker;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.EventPublisherPort;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RabbitMqIntegrationTest {

    @Autowired
    private EventPublisherPort eventPublisherPort;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;


    @Test
    void contextLoadsAndBeansAreCreated() {
        assertThat(eventPublisherPort).isNotNull();
    }
}
