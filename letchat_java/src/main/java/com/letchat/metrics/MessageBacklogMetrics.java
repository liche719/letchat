package com.letchat.metrics;

import com.letchat.config.RabbitMQConfig;
import com.letchat.mappers.ChatMessageOutboxMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息链路积压指标。
 *
 * <p>原来 outbox 积压、DLQ 堆积都只体现在日志里，RabbitMQ 挂十几分钟没人会发现。
 * 这里定时采样两个数，挂到 Micrometer，Prometheus 侧就能直接告警。
 */
@Component
@Slf4j
public class MessageBacklogMetrics {

    private final AtomicLong pendingOutbox = new AtomicLong();
    private final AtomicLong deadLetterDepth = new AtomicLong();

    @Resource
    private ChatMessageOutboxMapper chatMessageOutboxMapper;

    @Resource
    private AmqpAdmin amqpAdmin;

    public MessageBacklogMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("letchat.outbox.pending", pendingOutbox, AtomicLong::get)
                .description("待发布的消息事件数量")
                .register(meterRegistry);
        Gauge.builder("letchat.dlq.depth", deadLetterDepth, AtomicLong::get)
                .description("死信队列积压数量")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${letchat.metrics.backlog-interval-ms:30000}")
    public void refresh() {
        refreshPendingOutbox();
        refreshDeadLetterDepth();
    }

    void refreshPendingOutbox() {
        try {
            pendingOutbox.set(chatMessageOutboxMapper.countPending());
        } catch (Exception exception) {
            log.warn("采样 outbox 积压失败", exception);
        }
    }

    void refreshDeadLetterDepth() {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(RabbitMQConfig.CHAT_MESSAGE_DEAD_LETTER_QUEUE);
            deadLetterDepth.set(info == null || info.getMessageCount() < 0 ? 0 : info.getMessageCount());
        } catch (Exception exception) {
            // broker 不可达时保留上一次值，避免误报 0 掩盖积压
            log.warn("采样死信队列深度失败", exception);
        }
    }

    long pendingOutbox() {
        return pendingOutbox.get();
    }

    long deadLetterDepth() {
        return deadLetterDepth.get();
    }
}
