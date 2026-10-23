package com.letchat.mq;

import com.letchat.config.RabbitMQConfig;
import com.letchat.entity.po.ChatMessage;
import com.letchat.mappers.ChatMessageDeadLetterMapper;
import com.letchat.metrics.ChatMetrics;
import com.letchat.utils.JsonUtils;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 死信消费者。
 *
 * <p>原来 {@code chat.message.dlq} 只声明不消费，消息重试耗尽后进队列就再没人管。
 * 这里把死信落库（payload 保留可重放）+ 计数，然后才 ack；
 * 落库失败则 requeue，避免"为了消费掉而丢消息"。
 */
@Component("chatMessageDeadLetterConsumer")
@Slf4j
public class ChatMessageDeadLetterConsumer {

    @Resource
    private ChatMessageDeadLetterMapper chatMessageDeadLetterMapper;

    @Resource
    private ChatMetrics chatMetrics;

    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_DEAD_LETTER_QUEUE, concurrency = "1")
    public void onDeadLetter(Message amqpMessage,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String payload = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        String eventId = resolveEventId(payload);
        String reason = String.valueOf(amqpMessage.getMessageProperties().getHeaders().get("x-exception-message"));

        try {
            chatMessageDeadLetterMapper.insert(eventId, payload, truncate(reason));
        } catch (Exception exception) {
            // 落库失败不能 ack，否则这条消息就真的没了
            log.error("死信落库失败，requeue。eventId={}, payloadLength={}", eventId, payload.length(), exception);
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        chatMetrics.deadLettered();
        log.error("消息进入死信队列并已落库，需人工补偿：eventId={}, payloadLength={}", eventId, payload.length());
        channel.basicAck(deliveryTag, false);
    }

    private String resolveEventId(String payload) {
        try {
            ChatMessage chatMessage = JsonUtils.convertJson2Obj(payload, ChatMessage.class);
            if (chatMessage != null && chatMessage.getClientMessageId() != null) {
                return chatMessage.getClientMessageId();
            }
            if (chatMessage != null && chatMessage.getMessageId() != null) {
                return "message-" + chatMessage.getMessageId();
            }
        } catch (Exception ignored) {
            // 解析不出来也要落库，用长度+时间做标识
        }
        return "unparseable-" + Integer.toHexString(payload.hashCode());
    }

    private String truncate(String value) {
        if (value == null || "null".equals(value)) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
