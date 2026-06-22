package com.letchat.mq;

import com.alibaba.fastjson.JSON;
import com.letchat.config.RabbitMQConfig;
import com.letchat.entity.enums.UserContactTypeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSession;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.query.ChatSessionQuery;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatSessionMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component("chatMessageConsumer")
@Slf4j
public class ChatMessageConsumer {

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private TransactionTemplate transactionTemplate;

    private final ChatMessageRetryPolicy retryPolicy = new ChatMessageRetryPolicy();

    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE)
    public void processChatMessage(Message amqpMessage,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String payload = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        log.info("Received chat message from RabbitMQ: {}", payload);

        try {
            ChatMessage chatMessage = parsePayload(payload);
            boolean persisted = saveMessageIdempotently(chatMessage);
            channel.basicAck(deliveryTag, false);
            if (persisted) {
                log.info("Persisted chat message successfully, messageId={}", chatMessage.getMessageId());
            }
        } catch (InvalidChatMessageException e) {
            log.warn("Rejecting invalid chat message to DLQ: {}", payload, e);
            channel.basicReject(deliveryTag, false);
        } catch (Exception e) {
            retryOrDeadLetter(amqpMessage, channel, deliveryTag, payload, e);
        }
    }

    public boolean saveMessageIdempotently(ChatMessage chatMessage) {
        Boolean persisted = transactionTemplate.execute(status -> {
            if (isAlreadyPersisted(chatMessage)) {
                log.info("Skip duplicate chat message, messageId={}, sessionId={}, sendTime={}",
                        chatMessage.getMessageId(),
                        chatMessage.getSessionId(),
                        chatMessage.getSendTime());
                return false;
            }

            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(chatMessage.getSessionId());
            chatSession.setLastMessage(buildLastMessage(chatMessage));
            chatSession.setLastReceiveTime(chatMessage.getSendTime());
            chatSessionMapper.updateBySessionId(chatSession, chatSession.getSessionId());

            chatMessageMapper.insertOrUpdate(chatMessage);
            return true;
        });
        return Boolean.TRUE.equals(persisted);
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
            ChatMessage chatMessage = JSON.parseObject(trimmedPayload, ChatMessage.class);
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

    private boolean isAlreadyPersisted(ChatMessage chatMessage) {
        if (chatMessage.getMessageId() != null
                && chatMessageMapper.selectByMessageId(chatMessage.getMessageId()) != null) {
            return true;
        }
        return hasMessageFingerprint(chatMessage)
                && chatMessageMapper.selectByMessageFingerprint(chatMessage) != null;
    }

    private boolean hasMessageFingerprint(ChatMessage chatMessage) {
        return chatMessage.getSessionId() != null
                && chatMessage.getSendUserId() != null
                && chatMessage.getContactId() != null
                && chatMessage.getSendTime() != null
                && chatMessage.getMessageType() != null;
    }

    private String buildLastMessage(ChatMessage chatMessage) {
        if (UserContactTypeEnum.GROUP == UserContactTypeEnum.getByPrefix(chatMessage.getContactId())) {
            return chatMessage.getSendUserNickName() + ": " + chatMessage.getMessageContent();
        }
        return chatMessage.getMessageContent();
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
            rabbitTemplate.send(
                    RabbitMQConfig.CHAT_MESSAGE_EXCHANGE,
                    RabbitMQConfig.CHAT_MESSAGE_ROUTING_KEY,
                    retryMessage
            );
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
