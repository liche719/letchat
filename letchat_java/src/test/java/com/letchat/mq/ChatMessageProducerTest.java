package com.letchat.mq;

import com.letchat.entity.po.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ChatMessageProducer<ChatMessage> producer;

    @Test
    void returnsAfterBrokerConfirmsMessage() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(String.class), any(CorrelationData.class));

        producer.saveChatMessage(new ChatMessage().setMessageContent("hello"));

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(String.class), any(CorrelationData.class));
    }

    @Test
    void retriesNackAndEventuallySucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            boolean ack = attempts.incrementAndGet() == 3;
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "broker nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(String.class), any(CorrelationData.class));

        producer.saveChatMessage(new ChatMessage().setMessageContent("hello"));

        verify(rabbitTemplate, times(3))
                .convertAndSend(anyString(), anyString(), any(String.class), any(CorrelationData.class));
    }

    @Test
    void failsAfterInitialAttemptAndThreeRetries() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "broker nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(String.class), any(CorrelationData.class));

        assertThrows(
                AmqpException.class,
                () -> producer.saveChatMessage(new ChatMessage().setMessageContent("hello"))
        );

        verify(rabbitTemplate, times(4))
                .convertAndSend(anyString(), anyString(), any(String.class), any(CorrelationData.class));
    }
}
