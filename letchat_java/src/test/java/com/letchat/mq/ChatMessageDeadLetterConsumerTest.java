package com.letchat.mq;

import com.letchat.mappers.ChatMessageDeadLetterMapper;
import com.letchat.metrics.ChatMetrics;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatMessageDeadLetterConsumerTest {

    private static final String PAYLOAD =
            "{\"messageId\":42,\"clientMessageId\":\"client-42\",\"messageContent\":\"hello\"}";

    @Test
    void persistsDeadLetterThenAcks() throws Exception {
        ChatMessageDeadLetterMapper mapper = mock(ChatMessageDeadLetterMapper.class);
        ChatMetrics metrics = new ChatMetrics(new SimpleMeterRegistry());
        ChatMessageDeadLetterConsumer consumer = new ChatMessageDeadLetterConsumer();
        ReflectionTestUtils.setField(consumer, "chatMessageDeadLetterMapper", mapper);
        ReflectionTestUtils.setField(consumer, "chatMetrics", metrics);
        Channel channel = mock(Channel.class);

        consumer.onDeadLetter(message(PAYLOAD), channel, 7L);

        verify(mapper).insert(eq("client-42"), eq(PAYLOAD), anyString());
        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void requeuesInsteadOfAckingWhenPersistingFails() throws Exception {
        ChatMessageDeadLetterMapper mapper = mock(ChatMessageDeadLetterMapper.class);
        doThrow(new IllegalStateException("db down")).when(mapper).insert(anyString(), anyString(), anyString());
        ChatMessageDeadLetterConsumer consumer = new ChatMessageDeadLetterConsumer();
        ReflectionTestUtils.setField(consumer, "chatMessageDeadLetterMapper", mapper);
        ReflectionTestUtils.setField(consumer, "chatMetrics", new ChatMetrics(new SimpleMeterRegistry()));
        Channel channel = mock(Channel.class);

        consumer.onDeadLetter(message(PAYLOAD), channel, 9L);

        // 落库失败必须 requeue：ack 掉就等于把消息彻底丢了
        verify(channel).basicNack(9L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    private static Message message(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setHeaders(Map.of("x-exception-message", "broker nack"));
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
}
