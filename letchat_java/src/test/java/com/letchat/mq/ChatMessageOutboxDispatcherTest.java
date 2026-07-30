package com.letchat.mq;

import com.letchat.entity.po.ChatMessageOutbox;
import com.letchat.mappers.ChatMessageOutboxMapper;
import com.letchat.metrics.ChatMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageOutboxDispatcherTest {

    @Test
    void marksConfirmedEventPublishedAndRecordsMetric() {
        ChatMessageOutboxMapper mapper = mock(ChatMessageOutboxMapper.class);
        ChatMessageProducer<?> producer = mock(ChatMessageProducer.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChatMessageOutbox event = event();
        when(mapper.claimPending(anyString(), anyInt())).thenReturn(1);
        when(mapper.findClaimed(anyString())).thenReturn(List.of(event));

        new ChatMessageOutboxDispatcher(mapper, producer, new ChatMetrics(registry)).dispatchPending();

        verify(producer).publishPayload(event.getPayload(), event.getRoutingKey());
        verify(mapper).markPublished(org.mockito.ArgumentMatchers.eq(event.getEventId()), anyString());
        assertEquals(1.0, registry.get("letchat.outbox.published").counter().count());
    }

    @Test
    void schedulesBoundedRetryAndRecordsFailureMetric() {
        ChatMessageOutboxMapper mapper = mock(ChatMessageOutboxMapper.class);
        ChatMessageProducer<?> producer = mock(ChatMessageProducer.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChatMessageOutbox event = event();
        when(mapper.claimPending(anyString(), anyInt())).thenReturn(1);
        when(mapper.findClaimed(anyString())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker down"))
                .when(producer).publishPayload(event.getPayload(), event.getRoutingKey());

        new ChatMessageOutboxDispatcher(mapper, producer, new ChatMetrics(registry)).dispatchPending();

        verify(mapper).markFailed(anyString(), anyString(), anyInt(), anyLong(), anyString());
        assertEquals(1.0, registry.get("letchat.outbox.failed").counter().count());
    }

    @Test
    void doesNotPublishWhenAnotherInstanceClaimedAllEvents() {
        ChatMessageOutboxMapper mapper = mock(ChatMessageOutboxMapper.class);
        ChatMessageProducer<?> producer = mock(ChatMessageProducer.class);
        when(mapper.claimPending(anyString(), anyInt())).thenReturn(0);

        new ChatMessageOutboxDispatcher(
                mapper,
                producer,
                new ChatMetrics(new SimpleMeterRegistry())
        ).dispatchPending();

        verify(mapper).releaseStaleClaims(anyLong());
        org.mockito.Mockito.verifyNoInteractions(producer);
    }

    private ChatMessageOutbox event() {
        return new ChatMessageOutbox()
                .setEventId("event-1")
                .setSessionId("session-1")
                .setRoutingKey("chat.message.shard.1")
                .setPayload("{\"messageId\":1}")
                .setRetryCount(0);
    }
}
