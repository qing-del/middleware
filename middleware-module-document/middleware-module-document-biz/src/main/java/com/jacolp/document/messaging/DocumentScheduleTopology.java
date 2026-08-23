package com.jacolp.document.messaging;

import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.document.config.DocumentProperties;
import java.util.Objects;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ declarations for content-free document scheduling signals. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentScheduleTopology {

    public static final String EXCHANGE = "document.schedule.exchange";
    public static final String ROUTING_KEY = "document.schedule";
    public static final String QUEUE = "document.schedule.queue";
    public static final String RETRY_QUEUE = QUEUE + ".retry";
    public static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";
    public static final String FLUSH_LOG_DELAY_QUEUE = "document.schedule.flush-log.delay";

    private final DocumentProperties documentProperties;
    private final ReliableMessagingProperties messagingProperties;

    public DocumentScheduleTopology(DocumentProperties documentProperties,
                                    ReliableMessagingProperties messagingProperties) {
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
        this.messagingProperties = Objects.requireNonNull(messagingProperties, "messagingProperties must not be null");
    }

    @Bean
    public DirectExchange documentScheduleExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue documentScheduleQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Queue documentScheduleRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(QUEUE)
                .ttl(toQueueTtl(messagingProperties.getRetryQueueDelayMs(), "jacolp.messaging.retry-queue-delay-ms"))
                .build();
    }

    @Bean
    public Queue documentScheduleDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding documentScheduleBinding(@Qualifier("documentScheduleQueue") Queue documentScheduleQueue,
                                           @Qualifier("documentScheduleExchange") DirectExchange documentScheduleExchange) {
        return BindingBuilder.bind(documentScheduleQueue).to(documentScheduleExchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue documentFlushLogDelayQueue() {
        return QueueBuilder.durable(FLUSH_LOG_DELAY_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .ttl(toQueueTtl(documentProperties.getFlushLog().getDelayMs(), "jacolp.document.flush-log.delay-ms"))
                .build();
    }

    private static int toQueueTtl(long delayMs, String propertyName) {
        if (delayMs <= 0 || delayMs > Integer.MAX_VALUE) {
            throw new IllegalStateException(propertyName + " must be between 1 and " + Integer.MAX_VALUE);
        }
        return Math.toIntExact(delayMs);
    }
}
