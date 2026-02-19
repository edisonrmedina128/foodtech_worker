package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.rest;

import com.foodtech.kitchen.worker.foodtech_worker.application.usecases.ProcessEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestEventController {

    private final ProcessEventUseCase processEventUseCase;

    @PostMapping("/publish")
    public ResponseEntity<String> publishEvent(
            @RequestParam(defaultValue = "TEST_EVENT") String eventType,
            @RequestBody String payload) {
        
        processEventUseCase.processAndPublish(eventType, payload);
        return ResponseEntity.ok("Event published successfully");
    }
}
