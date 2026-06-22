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

    public static final String CHAT_MESSAGE_QUEUE = "chat.message.queue";
    public static final String CHAT_MESSAGE_EXCHANGE = "chat.message.exchange";
    public static final String CHAT_MESSAGE_ROUTING_KEY = "chat.message.key";

    public static final String CHAT_MESSAGE_DEAD_LETTER_QUEUE = "chat.message.dlq";
    public static final String CHAT_MESSAGE_DEAD_LETTER_EXCHANGE = "chat.message.dlx";
    public static final String CHAT_MESSAGE_DEAD_LETTER_ROUTING_KEY = "chat.message.dead";

    @Bean
    public Queue chatMessageQueue() {
        return QueueBuilder.durable(CHAT_MESSAGE_QUEUE)
                .deadLetterExchange(CHAT_MESSAGE_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CHAT_MESSAGE_DEAD_LETTER_ROUTING_KEY)
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
