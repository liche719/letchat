package com.letchat.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {

    private final Counter outboxPublished;
    private final Counter outboxFailed;
    private final Counter webSocketConnected;
    private final Counter webSocketDisconnected;
    private final Counter deadLettered;
    private final Counter retried;

    public ChatMetrics(MeterRegistry meterRegistry) {
        outboxPublished = meterRegistry.counter("letchat.outbox.published");
        outboxFailed = meterRegistry.counter("letchat.outbox.failed");
        webSocketConnected = meterRegistry.counter("letchat.websocket.connected");
        webSocketDisconnected = meterRegistry.counter("letchat.websocket.disconnected");
        deadLettered = meterRegistry.counter("letchat.message.deadlettered");
        retried = meterRegistry.counter("letchat.message.retried");
    }

    public void outboxPublished() {
        outboxPublished.increment();
    }

    public void outboxFailed() {
        outboxFailed.increment();
    }

    public void webSocketConnected() {
        webSocketConnected.increment();
    }

    public void webSocketDisconnected() {
        webSocketDisconnected.increment();
    }

    /** 进入死信队列的消息数：这个值一涨就说明有消息再也到不了用户，需要告警。 */
    public void deadLettered() {
        deadLettered.increment();
    }

    /** 重投次数。 */
    public void retried() {
        retried.increment();
    }
}
