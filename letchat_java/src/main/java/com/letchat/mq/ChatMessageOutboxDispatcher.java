package com.letchat.mq;

import com.letchat.entity.po.ChatMessageOutbox;
import com.letchat.mappers.ChatMessageOutboxMapper;
import com.letchat.metrics.ChatMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ChatMessageOutboxDispatcher {

    private final ChatMessageOutboxMapper outboxMapper;
    private final ChatMessageProducer<?> producer;
    private final ChatMetrics chatMetrics;

    @Value("${letchat.outbox.batch-size:100}")
    private int batchSize;

    @Value("${letchat.outbox.max-retries:10}")
    private int maxRetries;

    @Value("${letchat.outbox.claim-timeout-seconds:300}")
    private long claimTimeoutSeconds;

    public ChatMessageOutboxDispatcher(ChatMessageOutboxMapper outboxMapper,
                                       ChatMessageProducer<?> producer,
                                       ChatMetrics chatMetrics) {
        this.outboxMapper = outboxMapper;
        this.producer = producer;
        this.chatMetrics = chatMetrics;
    }

    @Scheduled(fixedDelayString = "${letchat.outbox.dispatch-interval-ms:200}")
    public void dispatchPending() {
        outboxMapper.releaseStaleClaims(Math.max(1, claimTimeoutSeconds));
        String claimToken = UUID.randomUUID().toString();
        if (outboxMapper.claimPending(claimToken, Math.max(1, batchSize)) == 0) {
            return;
        }
        List<ChatMessageOutbox> pending = outboxMapper.findClaimed(claimToken);
        for (ChatMessageOutbox outbox : pending) {
            try {
                producer.publishPayload(outbox.getPayload(), outbox.getRoutingKey());
                outboxMapper.markPublished(outbox.getEventId(), claimToken);
                chatMetrics.outboxPublished();
            } catch (Exception exception) {
                int retryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
                long delaySeconds = Math.min(300, 1L << Math.min(8, retryCount));
                outboxMapper.markFailed(
                        outbox.getEventId(),
                        claimToken,
                        Math.max(1, maxRetries),
                        delaySeconds,
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
                );
                chatMetrics.outboxFailed();
                log.error("Failed to dispatch chat outbox event, eventId={}, retry={}",
                        outbox.getEventId(), retryCount + 1, exception);
            }
        }
    }
}
