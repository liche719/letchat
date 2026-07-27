package com.letchat.mq;

import com.alibaba.fastjson.JSON;
import com.letchat.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

@Component("chatMessageProducer")
@Slf4j
public class ChatMessageProducer<T> {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${letchat.rabbitmq.publisher-confirm-timeout-ms:3000}")
    private long publisherConfirmTimeoutMs = 3000;

    @Value("${letchat.rabbitmq.publisher-max-retries:3}")
    private int publisherMaxRetries = 3;

    @PostConstruct
    public void configurePublisherCallbacks() {
        rabbitTemplate.setMandatory(true);
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
        publishWithConfirm(correlationData -> rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHAT_MESSAGE_EXCHANGE,
                RabbitMQConfig.CHAT_MESSAGE_ROUTING_KEY,
                message,
                correlationData
        ));
    }

    public void republishChatMessage(Message message) {
        publishWithConfirm(correlationData -> rabbitTemplate.send(
                RabbitMQConfig.CHAT_MESSAGE_EXCHANGE,
                RabbitMQConfig.CHAT_MESSAGE_ROUTING_KEY,
                message,
                correlationData
        ));
    }

    private void publishWithConfirm(ConfirmedSend send) {
        int attempts = Math.max(0, publisherMaxRetries) + 1;
        AmqpException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            CorrelationData correlationData = new CorrelationData("chat-message-" + UUID.randomUUID());
            try {
                send.send(correlationData);
                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(Math.max(1, publisherConfirmTimeoutMs), TimeUnit.MILLISECONDS);
                if (!confirm.isAck()) {
                    throw new AmqpException("RabbitMQ nack: " + confirm.getReason());
                }
                if (correlationData.getReturned() != null) {
                    throw new AmqpException("RabbitMQ returned unroutable chat message");
                }
                log.debug("RabbitMQ confirmed chat message, correlationId={}, attempt={}",
                        correlationData.getId(), attempt);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AmqpException("Interrupted while waiting for RabbitMQ publisher confirm", e);
            } catch (ExecutionException | TimeoutException | AmqpException e) {
                lastFailure = e instanceof AmqpException
                        ? (AmqpException) e
                        : new AmqpException("RabbitMQ publisher confirm failed", e);
                log.warn("RabbitMQ publish attempt failed, attempt={}, maxAttempts={}, correlationId={}, cause={}",
                        attempt, attempts, correlationData.getId(), e.getMessage());
            }
        }
        throw new AmqpException("Failed to publish chat message after " + attempts + " attempts", lastFailure);
    }

    @FunctionalInterface
    private interface ConfirmedSend {
        void send(CorrelationData correlationData);
    }
}
