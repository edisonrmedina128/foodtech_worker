package com.foodtech.kitchen.worker.foodtech_worker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodEvent {
    private String eventId;
    private String eventType; // e.g., "ORDER_CREATED", "ORDER_PREPARED"
    private String payload;   // JSON payload or details
    private LocalDateTime timestamp;
}
