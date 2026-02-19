package com.foodtech.kitchen.worker.foodtech_worker.application.ports.output;

import com.foodtech.kitchen.worker.foodtech_worker.domain.model.FoodEvent;

public interface EventPublisherPort {
    void publish(FoodEvent event);
}
