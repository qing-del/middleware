package com.jacolp.audio.biz.config;

import com.jacolp.audio.biz.service.RabbitMqTaskPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "jacolp.audio", name = "queue-type", havingValue = "rabbitmq")
public class RabbitMqAudioTaskConfiguration {
    public static final String QUEUE = "audio.generate.queue";

    @Bean
    public DirectExchange audioGenerateExchange() {
        return new DirectExchange(RabbitMqTaskPublisher.EXCHANGE, true, false);
    }

    @Bean
    public Queue audioGenerateQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding audioGenerateBinding(Queue audioGenerateQueue, DirectExchange audioGenerateExchange) {
        return BindingBuilder.bind(audioGenerateQueue).to(audioGenerateExchange).with(RabbitMqTaskPublisher.ROUTING_KEY);
    }
}
