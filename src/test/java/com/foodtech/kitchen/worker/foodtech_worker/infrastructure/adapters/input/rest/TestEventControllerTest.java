package com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.rest;

import com.foodtech.kitchen.worker.foodtech_worker.application.usecases.ProcessEventUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestEventController.class)
class TestEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessEventUseCase processEventUseCase;


    @Test
    void publishEvent_ShouldCallUseCaseAndReturnOk() throws Exception {
        // Arrange
        String eventType = "TEST_EVENT";
        String payload = "{\"key\":\"value\"}";

        // Act & Assert
        mockMvc.perform(post("/api/test/publish")
                        .param("eventType", eventType)
                        .content(payload)
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string("Event published successfully"));

        verify(processEventUseCase).processAndPublish(eq(eventType), eq(payload));
    }
}
