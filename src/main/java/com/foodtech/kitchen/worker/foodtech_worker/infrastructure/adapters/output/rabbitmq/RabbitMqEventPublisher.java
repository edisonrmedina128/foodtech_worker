package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.output.rabbitmq;

import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.EventPublisherPort;
import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqEventPublisher implements EventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Value("${foodtech.rabbitmq.exchange}")
    private String exchange;

    @Value("${foodtech.rabbitmq.routingkey}")
    private String routingKey;

    @Override
    public void publish(FoodEvent event) {
        log.info("Publishing event to RabbitMQ: {}", event);
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
        log.info("Event sent to exchange: {}, routingKey: {}", exchange, routingKey);
    }
}
