package com.jacolp.middleware.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
public class EventMessageCodec {
    private final ObjectMapper objectMapper;

    public EventMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope decode(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), EventEnvelope.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid domain event envelope", e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return objectMapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid payload for " + envelope.eventType(), e);
        }
    }
}
