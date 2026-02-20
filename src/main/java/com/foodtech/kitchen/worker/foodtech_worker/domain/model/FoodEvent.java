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
    private String eventType;
    private String payload;
    private LocalDateTime timestamp;
}
