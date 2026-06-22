package com.letchat.mq;

import com.alibaba.fastjson.JSON;
import com.letchat.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.UUID;

@Component("chatMessageProducer")
@Slf4j
public class ChatMessageProducer<T> {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void configurePublisherCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String id = correlationData == null ? "unknown" : correlationData.getId();
                log.error("RabbitMQ did not confirm chat message, correlationId={}, cause={}", id, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "RabbitMQ returned unroutable chat message, exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
    }

    public void saveChatMessage(T payload) {
        String message = JSON.toJSONString(payload);
        CorrelationData correlationData = new CorrelationData("chat-message-" + UUID.randomUUID());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_MESSAGE_EXCHANGE,
                    RabbitMQConfig.CHAT_MESSAGE_ROUTING_KEY,
                    message,
                    correlationData
            );
            log.info("Sent chat message to RabbitMQ, correlationId={}, payload={}", correlationData.getId(), message);
        } catch (AmqpException e) {
            log.error("Failed to send chat message to RabbitMQ, payload={}", message, e);
            throw e;
        }
    }
}
