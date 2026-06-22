package com.letchat.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitMQConfigTest {

    @Test
    void chatQueueRoutesRejectedMessagesToDeadLetterQueue() {
        RabbitMQConfig config = new RabbitMQConfig();

        Queue queue = config.chatMessageQueue();

        assertEquals(RabbitMQConfig.CHAT_MESSAGE_QUEUE, queue.getName());
        assertEquals(
                RabbitMQConfig.CHAT_MESSAGE_DEAD_LETTER_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange")
        );
        assertEquals(
                RabbitMQConfig.CHAT_MESSAGE_DEAD_LETTER_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key")
        );
    }

    @Test
    void deadLetterQueueUsesDedicatedRoute() {
        RabbitMQConfig config = new RabbitMQConfig();

        Queue deadLetterQueue = config.chatMessageDeadLetterQueue();

        assertEquals(RabbitMQConfig.CHAT_MESSAGE_DEAD_LETTER_QUEUE, deadLetterQueue.getName());
        assertEquals(RabbitMQConfig.CHAT_MESSAGE_DEAD_LETTER_EXCHANGE, config.chatMessageDeadLetterExchange().getName());
    }
}
