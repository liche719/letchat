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

    public ChatMetrics(MeterRegistry meterRegistry) {
        outboxPublished = meterRegistry.counter("letchat.outbox.published");
        outboxFailed = meterRegistry.counter("letchat.outbox.failed");
        webSocketConnected = meterRegistry.counter("letchat.websocket.connected");
        webSocketDisconnected = meterRegistry.counter("letchat.websocket.disconnected");
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
}
