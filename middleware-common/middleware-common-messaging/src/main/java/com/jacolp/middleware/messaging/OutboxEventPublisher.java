package com.jacolp.middleware.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes events to the database transaction only; this class never calls RabbitMQ. */
@Service
public class OutboxEventPublisher {
    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final ReliableMessagingProperties properties;

    public OutboxEventPublisher(OutboxRepository repository, ObjectMapper objectMapper,
                                ReliableMessagingProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope publish(String eventType, String routingKey, String aggregateType,
                                 Object aggregateId, String correlationId, Object payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), eventType, 1,
                aggregateType, String.valueOf(aggregateId), Instant.now(), correlationId,
                objectMapper.valueToTree(payload));
        String serialized = serialize(envelope);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
            throw new IllegalArgumentException("event payload exceeds configured maximum size");
        }
        repository.insert(envelope, routingKey, serialized);
        return envelope;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> List<EventEnvelope> publishPartitioned(String eventType, String routingKey,
            String aggregateType, Object aggregateId, String correlationId, List<T> items) {
        Objects.requireNonNull(items, "items must not be null");
        int shardSize = Math.max(1, properties.getShardSize());
        List<EventEnvelope> events = new ArrayList<>();
        for (int start = 0; start < items.size(); start += shardSize) {
            int end = Math.min(items.size(), start + shardSize);
            events.add(publish(eventType, routingKey, aggregateType, aggregateId, correlationId,
                    new EventShard<>(start / shardSize, (items.size() + shardSize - 1) / shardSize,
                            List.copyOf(items.subList(start, end)))));
        }
        return List.copyOf(events);
    }

    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize event " + envelope.eventType(), e);
        }
    }

    public record EventShard<T>(int shardIndex, int shardCount, List<T> items) {
    }
}
