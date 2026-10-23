package com.letchat.metrics;

import com.letchat.mappers.ChatMessageOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageBacklogMetricsTest {

    @Test
    void publishesBacklogAndDeadLetterDepthAsGauges() {
        ChatMessageOutboxMapper outbox = mock(ChatMessageOutboxMapper.class);
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        when(outbox.countPending()).thenReturn(37L);
        when(amqpAdmin.getQueueInfo(anyString())).thenReturn(new QueueInformation("chat.message.dlq", 5, 0));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageBacklogMetrics metrics = new MessageBacklogMetrics(registry);
        ReflectionTestUtils.setField(metrics, "chatMessageOutboxMapper", outbox);
        ReflectionTestUtils.setField(metrics, "amqpAdmin", amqpAdmin);

        metrics.refresh();

        assertEquals(37L, metrics.pendingOutbox());
        assertEquals(5L, metrics.deadLetterDepth());
        assertEquals(37D, registry.get("letchat.outbox.pending").gauge().value());
        assertEquals(5D, registry.get("letchat.dlq.depth").gauge().value());
    }

    @Test
    void keepsLastKnownValueWhenBrokerIsUnreachable() {
        ChatMessageOutboxMapper outbox = mock(ChatMessageOutboxMapper.class);
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        when(outbox.countPending()).thenReturn(12L);
        when(amqpAdmin.getQueueInfo(anyString())).thenReturn(new QueueInformation("chat.message.dlq", 3, 0));
        MessageBacklogMetrics metrics = new MessageBacklogMetrics(new SimpleMeterRegistry());
        ReflectionTestUtils.setField(metrics, "chatMessageOutboxMapper", outbox);
        ReflectionTestUtils.setField(metrics, "amqpAdmin", amqpAdmin);
        metrics.refresh();

        when(amqpAdmin.getQueueInfo(anyString())).thenThrow(new IllegalStateException("broker down"));
        metrics.refresh();

        // broker 不可达时不能把积压报成 0，否则告警会被掩盖
        assertEquals(3L, metrics.deadLetterDepth());
    }
}
