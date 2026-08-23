package com.jacolp.document.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.config.DocumentProperties;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes small durable scheduling signals; document state stays in Redis and MySQL. */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentSchedulePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentProperties documentProperties;
    private final ReliableMessagingProperties messagingProperties;

    public DocumentSchedulePublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                                     DocumentProperties documentProperties,
                                     ReliableMessagingProperties messagingProperties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
        this.messagingProperties = Objects.requireNonNull(messagingProperties, "messagingProperties must not be null");
    }

    public void scheduleFlushLog(long documentId) {
        Message message = newScheduleMessage(documentId, DocumentScheduleType.FLUSH_LOG,
                System.currentTimeMillis() + documentProperties.getFlushLog().getDelayMs(), null);
        rabbitTemplate.invoke(operations -> {
            operations.send("", DocumentScheduleTopology.FLUSH_LOG_DELAY_QUEUE, message);
            operations.waitForConfirmsOrDie(messagingProperties.getConfirmTimeoutMs());
            return null;
        });
    }

    Message newScheduleMessage(long documentId, DocumentScheduleType type, long triggerTime, String closeToken) {
        DocumentScheduleMessage schedule = new DocumentScheduleMessage(documentId, type, triggerTime, closeToken);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(serialize(schedule), properties);
    }

    private byte[] serialize(DocumentScheduleMessage schedule) {
        try {
            return objectMapper.writeValueAsBytes(schedule);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize document scheduling signal", exception);
        }
    }
}
