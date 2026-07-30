package com.letchat.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String CHAT_MESSAGE_QUEUE = "chat.message.queue.0";
    public static final String CHAT_MESSAGE_QUEUE_1 = "chat.message.queue.1";
    public static final String CHAT_MESSAGE_QUEUE_2 = "chat.message.queue.2";
    public static final String CHAT_MESSAGE_QUEUE_3 = "chat.message.queue.3";
    public static final String CHAT_MESSAGE_EXCHANGE = "chat.message.exchange";
    public static final String CHAT_MESSAGE_ROUTING_KEY = "chat.message.shard.0";
    public static final int CHAT_MESSAGE_SHARD_COUNT = 4;

    public static final String CHAT_MESSAGE_DEAD_LETTER_QUEUE = "chat.message.dlq";
    public static final String CHAT_MESSAGE_DEAD_LETTER_EXCHANGE = "chat.message.dlx";
    public static final String CHAT_MESSAGE_DEAD_LETTER_ROUTING_KEY = "chat.message.dead";

    @Bean
    public Queue chatMessageQueue() {
        return orderedQueue(CHAT_MESSAGE_QUEUE);
    }

    @Bean
    public Queue chatMessageQueue1() {
        return orderedQueue(CHAT_MESSAGE_QUEUE_1);
    }

    @Bean
    public Queue chatMessageQueue2() {
        return orderedQueue(CHAT_MESSAGE_QUEUE_2);
    }

    @Bean
    public Queue chatMessageQueue3() {
        return orderedQueue(CHAT_MESSAGE_QUEUE_3);
    }

    private Queue orderedQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(CHAT_MESSAGE_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CHAT_MESSAGE_DEAD_LETTER_ROUTING_KEY)
                .singleActiveConsumer()
                .build();
    }

    @Bean
    public DirectExchange chatMessageExchange() {
        return new DirectExchange(CHAT_MESSAGE_EXCHANGE);
    }

    @Bean
    public Binding chatMessageBinding() {
        return BindingBuilder.bind(chatMessageQueue())
                .to(chatMessageExchange())
                .with(CHAT_MESSAGE_ROUTING_KEY);
    }

    @Bean
    public Binding chatMessageBinding1() {
        return BindingBuilder.bind(chatMessageQueue1()).to(chatMessageExchange()).with(routingKey(1));
    }

    @Bean
    public Binding chatMessageBinding2() {
        return BindingBuilder.bind(chatMessageQueue2()).to(chatMessageExchange()).with(routingKey(2));
    }

    @Bean
    public Binding chatMessageBinding3() {
        return BindingBuilder.bind(chatMessageQueue3()).to(chatMessageExchange()).with(routingKey(3));
    }

    public static String routingKey(int shard) {
        return "chat.message.shard." + shard;
    }

    public static String routingKeyForSession(String sessionId) {
        return routingKey(Math.floorMod(sessionId.hashCode(), CHAT_MESSAGE_SHARD_COUNT));
    }

    @Bean
    public Queue chatMessageDeadLetterQueue() {
        return QueueBuilder.durable(CHAT_MESSAGE_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public DirectExchange chatMessageDeadLetterExchange() {
        return new DirectExchange(CHAT_MESSAGE_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Binding chatMessageDeadLetterBinding() {
        return BindingBuilder.bind(chatMessageDeadLetterQueue())
                .to(chatMessageDeadLetterExchange())
                .with(CHAT_MESSAGE_DEAD_LETTER_ROUTING_KEY);
    }
}
