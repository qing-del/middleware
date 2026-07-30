package com.jacolp.middleware.messaging;

import java.util.Objects;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventRetryPublisher {
    public static final String RETRY_COUNT_HEADER = "x-application-retry-count";

    private final RabbitTemplate rabbitTemplate;
    private final ReliableMessagingProperties properties;

    public EventRetryPublisher(RabbitTemplate rabbitTemplate, ReliableMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /** Republishes to a bounded retry queue; the caller may ACK only after this method returns. */
    public boolean retryOrDeadLetter(String mainQueue, Message original, RuntimeException failure) {
        Objects.requireNonNull(mainQueue, "mainQueue must not be null");
        int retries = retryCount(original) + 1;
        String destination = retries >= properties.getMaxRetries()
                ? EventTopology.deadLetterQueue(mainQueue)
                : EventTopology.retryQueue(mainQueue);
        Message copy = MessageBuilder.fromMessage(original)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader(RETRY_COUNT_HEADER, retries)
                .setHeader("x-last-error", safeError(failure))
                .build();
        rabbitTemplate.invoke(operations -> {
            operations.send("", destination, copy);
            operations.waitForConfirmsOrDie(properties.getConfirmTimeoutMs());
            return null;
        });
        return retries < properties.getMaxRetries();
    }

    private static int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String safeError(RuntimeException failure) {
        String value = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
