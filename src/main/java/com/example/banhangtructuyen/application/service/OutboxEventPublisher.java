package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.domain.model.OutboxEvent;
import com.example.banhangtructuyen.domain.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publishEvent(final String aggregateType, final String aggregateId, final String eventType, final Object payload) {
        try {
            final String payloadJson = objectMapper.writeValueAsString(payload);
            final OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
