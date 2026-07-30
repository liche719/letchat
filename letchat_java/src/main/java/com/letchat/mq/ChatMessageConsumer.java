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

    private final ChatMessageRetryPolicy retryPolicy = new ChatMessageRetryPolicy();

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
            boolean firstDelivery = deliverIdempotently(chatMessage);
            channel.basicAck(deliveryTag, false);
            if (firstDelivery) {
                log.debug("Delivered chat message notification, messageId={}", chatMessage.getMessageId());
            }
        } catch (InvalidChatMessageException e) {
            log.warn("Rejecting invalid chat message to DLQ: {}", payload, e);
            channel.basicReject(deliveryTag, false);
        } catch (Exception e) {
            retryOrDeadLetter(amqpMessage, channel, deliveryTag, payload, e);
        }
    }

    public boolean deliverIdempotently(ChatMessage chatMessage) {
        String eventId = eventId(chatMessage);
        Boolean firstDelivery = transactionTemplate.execute(
                status -> chatMessageInboxMapper.insertIfAbsent(eventId) == 1
        );
        if (!Boolean.TRUE.equals(firstDelivery)) {
            log.debug("Skip duplicate chat notification, eventId={}", eventId);
            return false;
        }
        messageHandler.sendMessage(CopyTools.copy(chatMessage, MessageSendDto.class));
        return true;
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
            log.error("Chat message exceeded retry limit, routing to DLQ: {}", payload, cause);
            channel.basicReject(deliveryTag, false);
            return;
        }

        Message retryMessage = retryPolicy.buildRetryMessage(amqpMessage);
        try {
            chatMessageProducer.republishChatMessage(retryMessage);
            channel.basicAck(deliveryTag, false);
            log.warn("Republished chat message for retry, retryCount={}, payload={}",
                    retryPolicy.getRetryCount(retryMessage),
                    payload,
                    cause);
        } catch (Exception publishFailure) {
            log.error("Failed to republish chat message retry, requeueing original payload={}", payload, publishFailure);
            channel.basicNack(deliveryTag, false, true);
        }
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
