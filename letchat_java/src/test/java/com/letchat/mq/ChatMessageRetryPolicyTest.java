package com.letchat.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageRetryPolicyTest {

    @Test
    void retryMessageIncrementsPersistentRetryHeader() {
        ChatMessageRetryPolicy retryPolicy = new ChatMessageRetryPolicy(3);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("traceId", "trace-1");
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        Message retryMessage = retryPolicy.buildRetryMessage(message);

        assertEquals(1, retryPolicy.getRetryCount(retryMessage));
        assertEquals("trace-1", retryMessage.getMessageProperties().getHeaders().get("traceId"));
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, retryMessage.getMessageProperties().getContentType());
        assertEquals("PERSISTENT", retryMessage.getMessageProperties().getDeliveryMode().name());
    }

    @Test
    void retryStopsAtConfiguredLimit() {
        ChatMessageRetryPolicy retryPolicy = new ChatMessageRetryPolicy(2);
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertTrue(retryPolicy.canRetry(message));
        message = retryPolicy.buildRetryMessage(message);
        assertTrue(retryPolicy.canRetry(message));
        message = retryPolicy.buildRetryMessage(message);

        assertFalse(retryPolicy.canRetry(message));
        assertEquals(2, retryPolicy.getRetryCount(message));
    }
}
