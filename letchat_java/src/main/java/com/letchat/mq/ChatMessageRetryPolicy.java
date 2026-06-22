package com.letchat.mq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

import java.util.Map;

public class ChatMessageRetryPolicy {

    public static final String RETRY_COUNT_HEADER = "x-letchat-retry-count";

    private final int maxRetries;

    public ChatMessageRetryPolicy() {
        this(3);
    }

    public ChatMessageRetryPolicy(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        this.maxRetries = maxRetries;
    }

    public boolean canRetry(Message message) {
        return getRetryCount(message) < maxRetries;
    }

    public int getRetryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public Message buildRetryMessage(Message message) {
        MessageProperties sourceProperties = message.getMessageProperties();
        MessageProperties retryProperties = new MessageProperties();
        retryProperties.setContentType(sourceProperties.getContentType());
        retryProperties.setContentEncoding(sourceProperties.getContentEncoding());
        retryProperties.setCorrelationId(sourceProperties.getCorrelationId());
        retryProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        copyHeaders(sourceProperties.getHeaders(), retryProperties);
        retryProperties.setHeader(RETRY_COUNT_HEADER, getRetryCount(message) + 1);
        return new Message(message.getBody(), retryProperties);
    }

    private void copyHeaders(Map<String, Object> headers, MessageProperties target) {
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            target.setHeader(entry.getKey(), entry.getValue());
        }
    }
}
