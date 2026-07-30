package com.jacolp.middleware.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "jacolp.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ReliableMessagingProperties properties;
    private final String claimant = UUID.randomUUID().toString();

    public OutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
                       ReliableMessagingProperties properties) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${jacolp.messaging.poll-delay-ms:1000}")
    public void relay() {
        List<OutboxRecord> records;
        try {
            records = repository.claimBatch(claimant, properties.getBatchSize(), properties.getClaimSeconds());
        } catch (RuntimeException e) {
            log.warn("Unable to claim outbox events: {}", e.getMessage());
            return;
        }
        records.forEach(this::publish);
    }

    void publish(OutboxRecord record) {
        try {
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            messageProperties.setMessageId(record.eventId());
            messageProperties.setHeader("eventId", record.eventId());
            messageProperties.setHeader("eventType", record.eventType());
            Message message = new Message(record.payload().getBytes(StandardCharsets.UTF_8), messageProperties);
            rabbitTemplate.invoke(operations -> {
                operations.send(EventTopology.EXCHANGE, record.routingKey(), message);
                operations.waitForConfirmsOrDie(properties.getConfirmTimeoutMs());
                return null;
            });
            if (!repository.markPublished(record.id(), claimant)) {
                log.warn("Outbox publish confirmation lost ownership, eventId={}", record.eventId());
            }
        } catch (RuntimeException e) {
            int retryCount = record.retryCount() + 1;
            boolean dead = retryCount >= properties.getMaxRetries();
            Duration backoff = retryBackoff(retryCount);
            repository.markFailed(record.id(), claimant, retryCount, LocalDateTime.now().plus(backoff),
                    e.getClass().getSimpleName() + ": " + e.getMessage(), dead);
            log.warn("Outbox publish failed, eventId={}, retryCount={}, dead={}",
                    record.eventId(), retryCount, dead);
        }
    }

    Duration retryBackoff(int retryCount) {
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 30);
        Duration candidate;
        try {
            candidate = properties.getInitialBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            candidate = properties.getMaxBackoff();
        }
        return candidate.compareTo(properties.getMaxBackoff()) > 0 ? properties.getMaxBackoff() : candidate;
    }
}
