package com.jacolp.middleware.messaging.config;

import java.util.ArrayList;
import java.util.List;

import com.jacolp.middleware.messaging.constant.EventTopology;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReliableMessagingProperties.class)
public class ReliableMessagingConfiguration {

    @Bean
    Declarables domainEventTopology(ReliableMessagingProperties properties) {
        TopicExchange exchange = new TopicExchange(EventTopology.EXCHANGE, true, false);
        List<Declarable> declarations = new ArrayList<>();
        declarations.add(exchange);
        // “笔记”模块所需要用于“审核”的队列
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.NOTE_QUEUE,
                "audit.reviewed", "audit.application.accepted", "audit.application.rejected",
                "audit.application.cancelled", "audit.application.cancel-rejected");
        // “媒体”模块所需要用于“审核”的队列
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.MEDIA_QUEUE,
                "audit.reviewed", "audit.application.accepted", "audit.application.rejected",
                "audit.application.cancelled", "audit.application.cancel-rejected");
        // 使用审核的模块
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.AUDIT_QUEUE,
                "audit.application.requested", "audit.application.cancel-requested");
        // 用户存储额释放 MQ QUEUE
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.SYSTEM_QUEUE, "storage.released");
        // 管理员发送邮件 QUEUE
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.EMAIL_QUEUE, "email.send-requested");
        // 媒体数据-图片 删除异步任务 MQ  保证最终一致性
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.MEDIA_DELETE_QUEUE, "media.resource.delete-requested");

        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.AUDIT_PROJECTION_QUEUE,
                "user.profile-changed");
        return new Declarables(declarations);
    }

    private static void addQueue(List<Declarable> declarations, TopicExchange exchange,
                                 long retryDelayMs, String queueName, String... routingKeys) {
        Queue main = QueueBuilder.durable(queueName)
                .deadLetterExchange("")
                .deadLetterRoutingKey(EventTopology.deadLetterQueue(queueName))
                .build();
        Queue retry = QueueBuilder.durable(EventTopology.retryQueue(queueName))
                .deadLetterExchange("")
                .deadLetterRoutingKey(queueName)
                .ttl(Math.toIntExact(retryDelayMs))
                .build();
        Queue dead = QueueBuilder.durable(EventTopology.deadLetterQueue(queueName)).build();
        declarations.add(main);
        declarations.add(retry);
        declarations.add(dead);
        for (String routingKey : routingKeys) {
            declarations.add(BindingBuilder.bind(main).to(exchange).with(routingKey));
        }
    }
}
