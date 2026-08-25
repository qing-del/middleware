package com.jacolp.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.application.compact.DocumentCompactService;
import com.jacolp.document.application.close.DocumentCloseService;
import com.jacolp.document.application.flush.DocumentFlushLogResult;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import com.jacolp.document.config.DocumentProperties;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 消费文档调度信号；每次处理都会重新检查持久化状态，不依赖消息只投递一次。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentScheduleConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentScheduleConsumer.class);

    private final ObjectMapper objectMapper;
    private final DocumentFlushLogService flushLogService;
    private final DocumentCompactService compactService;
    private final DocumentCloseService closeService;
    private final DocumentSchedulePublisher schedulePublisher;
    private final DocumentProperties documentProperties;
    private final EventRetryPublisher retryPublisher;

    /** 创建会重新读取 Redis/MySQL 事实状态的调度消费者。 */
    public DocumentScheduleConsumer(ObjectMapper objectMapper, DocumentFlushLogService flushLogService,
                                    DocumentCompactService compactService, DocumentCloseService closeService,
                                    DocumentSchedulePublisher schedulePublisher, DocumentProperties documentProperties,
                                    EventRetryPublisher retryPublisher) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.flushLogService = Objects.requireNonNull(flushLogService, "flushLogService must not be null");
        this.compactService = Objects.requireNonNull(compactService, "compactService must not be null");
        this.closeService = Objects.requireNonNull(closeService, "closeService must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
        this.retryPublisher = Objects.requireNonNull(retryPublisher, "retryPublisher must not be null");
    }

    /** 分派 FLUSH_LOG、COMPACT、CLOSE 信号；失败时沿用可靠消息重试/DLQ 语义。 */
    @RabbitListener(queues = DocumentScheduleTopology.QUEUE)
    public void onMessage(Message message) {
        try {
            DocumentScheduleMessage schedule = objectMapper.readValue(message.getBody(), DocumentScheduleMessage.class);
            if (schedule.type() == DocumentScheduleType.FLUSH_LOG) {
                // 刷盘后按真实批次大小选择立即压缩或延迟压缩，消息本身不携带更新列表。
                DocumentFlushLogResult result = flushLogService.flush(schedule.documentId());
                if (result.processedCount() >= documentProperties.getCompact().getMaxUnmergedOps()
                        || result.processedBytes() >= documentProperties.getCompact().getMaxUnmergedBytes()) {
                    schedulePublisher.scheduleCompactImmediately(schedule.documentId());
                } else {
                    schedulePublisher.scheduleCompact(schedule.documentId());
                }
            } else if (schedule.type() == DocumentScheduleType.COMPACT) {
                // COMPACT 内部通过日志位点和快照指针 CAS 处理重复、并发及延迟消息。
                compactService.compact(schedule.documentId());
            } else if (schedule.type() == DocumentScheduleType.CLOSE) {
                closeService.close(schedule.documentId(), schedule.closeToken());
            } else {
                throw new IllegalArgumentException("unsupported document schedule type: " + schedule.type());
            }
        } catch (Exception exception) {
            // 无论是反序列化失败还是业务失败，都交给现有 retry/DLQ 基础设施决定后续去向。
            RuntimeException failure = exception instanceof RuntimeException runtime ? runtime
                    : new IllegalArgumentException("invalid document scheduling signal", exception);
            boolean retrying = retryPublisher.retryOrDeadLetter(DocumentScheduleTopology.QUEUE, message, failure);
            log.warn("Document scheduling signal failed; retrying={}: {}", retrying, failure.getMessage());
        }
    }
}
