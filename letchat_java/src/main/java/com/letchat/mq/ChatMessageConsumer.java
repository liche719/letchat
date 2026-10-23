package com.letchat.mq;

import com.letchat.config.RabbitMQConfig;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.po.ChatMessage;
import com.letchat.mappers.ChatMessageInboxMapper;
import com.letchat.utils.CopyTools;
import com.letchat.utils.JsonUtils;
import com.letchat.websocket.MessageHandler;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component("chatMessageConsumer")
@Slf4j
public class ChatMessageConsumer {

    @Resource
    private ChatMessageInboxMapper chatMessageInboxMapper;

    @Resource
    private ChatMessageProducer<?> chatMessageProducer;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private com.letchat.metrics.ChatMetrics chatMetrics;

    /** 重试次数可配置：之前硬编码 3，配置项改了也不生效。 */
    @org.springframework.beans.factory.annotation.Value("${letchat.mq.max-retries:3}")
    private int maxRetries = 3;

    private ChatMessageRetryPolicy retryPolicy = new ChatMessageRetryPolicy();

    @PostConstruct
    void configureRetryPolicy() {
        this.retryPolicy = new ChatMessageRetryPolicy(maxRetries);
        log.info("Chat message consumer retry policy initialized, maxRetries={}", maxRetries);
    }

    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE, concurrency = "1")
    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE_1, concurrency = "1")
    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE_2, concurrency = "1")
    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE_3, concurrency = "1")
    public void processChatMessage(Message amqpMessage,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String payload = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        log.debug("Received chat message notification, payloadLength={}", payload.length());

        try {
            ChatMessage chatMessage = parsePayload(payload);
            deliverIdempotently(chatMessage);
            channel.basicAck(deliveryTag, false);
        } catch (InvalidChatMessageException e) {
            log.warn("Rejecting invalid chat message to DLQ: {}", payload, e);
            channel.basicReject(deliveryTag, false);
        } catch (Exception e) {
            retryOrDeadLetter(amqpMessage, channel, deliveryTag, payload, e);
        }
    }

    public boolean deliverIdempotently(ChatMessage chatMessage) {
        String eventId = eventId(chatMessage);
        Boolean firstDelivery = transactionTemplate.execute(status -> {
            if (chatMessageInboxMapper.insertIfAbsent(eventId) != 1) {
                log.debug("Skip duplicate chat notification, eventId={}", eventId);
                return false;
            }
            // 推送与 inbox 去重记录放在同一个事务里：推送抛异常时去重记录一并回滚，
            // broker 重投时这条消息仍会被当作首次投递重新推送。
            // 否则「先提交去重记录、再推送」在推送失败后会让重投被判定为重复而直接 ack，
            // 消息就永远到不了接收方。
            messageHandler.sendMessage(CopyTools.copy(chatMessage, MessageSendDto.class));
            return true;
        });
        if (Boolean.TRUE.equals(firstDelivery)) {
            log.debug("Delivered chat message notification, messageId={}", chatMessage.getMessageId());
        }
        return Boolean.TRUE.equals(firstDelivery);
    }

    private ChatMessage parsePayload(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new InvalidChatMessageException("message body is empty");
        }
        String trimmedPayload = payload.trim();
        if (!trimmedPayload.startsWith("{")) {
            throw new InvalidChatMessageException("message body is not JSON");
        }
        try {
            ChatMessage chatMessage = JsonUtils.convertJson2Obj(trimmedPayload, ChatMessage.class);
            if (chatMessage == null) {
                throw new InvalidChatMessageException("message body parsed to null");
            }
            return chatMessage;
        } catch (InvalidChatMessageException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidChatMessageException("message body cannot be parsed", e);
        }
    }

    private String eventId(ChatMessage chatMessage) {
        if (chatMessage.getClientMessageId() != null && !chatMessage.getClientMessageId().isBlank()) {
            return chatMessage.getClientMessageId();
        }
        if (chatMessage.getMessageId() != null) {
            return "message-" + chatMessage.getMessageId();
        }
        throw new InvalidChatMessageException("message id is missing");
    }

    private void retryOrDeadLetter(Message amqpMessage,
                                   Channel channel,
                                   long deliveryTag,
                                   String payload,
                                   Exception cause) throws IOException {
        if (!retryPolicy.canRetry(amqpMessage)) {
            // 不打印 payload：聊天正文不应进日志。eventId 足够定位，正文在死信表里
            log.error("Chat message exceeded retry limit, routing to DLQ, eventId={}, retryCount={}",
                    eventIdOf(amqpMessage), retryPolicy.getRetryCount(amqpMessage), cause);
            channel.basicReject(deliveryTag, false);
            return;
        }

        Message retryMessage = retryPolicy.buildRetryMessage(amqpMessage);
        try {
            chatMessageProducer.republishChatMessage(retryMessage);
            chatMetrics.retried();
            channel.basicAck(deliveryTag, false);
            log.warn("Republished chat message for retry, eventId={}, retryCount={}",
                    eventIdOf(amqpMessage),
                    retryPolicy.getRetryCount(retryMessage),
                    cause);
        } catch (Exception publishFailure) {
            log.error("Failed to republish chat message retry, requeueing original, eventId={}",
                    eventIdOf(amqpMessage), publishFailure);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private String eventIdOf(Message amqpMessage) {
        try {
            ChatMessage chatMessage = JsonUtils.convertJson2Obj(
                    new String(amqpMessage.getBody(), StandardCharsets.UTF_8), ChatMessage.class);
            if (chatMessage != null && chatMessage.getClientMessageId() != null) {
                return chatMessage.getClientMessageId();
            }
            if (chatMessage != null && chatMessage.getMessageId() != null) {
                return "message-" + chatMessage.getMessageId();
            }
        } catch (Exception ignored) {
            // 解析失败不影响重试流程，日志里退回占位
        }
        return "unknown";
    }

    private static class InvalidChatMessageException extends RuntimeException {
        InvalidChatMessageException(String message) {
            super(message);
        }

        InvalidChatMessageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
