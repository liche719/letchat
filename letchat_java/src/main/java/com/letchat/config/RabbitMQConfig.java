package com.letchat.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    // 定义队列名
    public static final String CHAT_MESSAGE_QUEUE = "chat.message.queue";
    public static final String CHAT_MESSAGE_EXCHANGE = "chat.message.exchange";
    public static final String CHAT_MESSAGE_ROUTING_KEY = "chat.message.key";

    // 创建队列
    @Bean
    public Queue chatMessageQueue() {
        return new Queue(CHAT_MESSAGE_QUEUE, true);
    }

    // 创建交换机
    @Bean
    public DirectExchange chatMessageExchange() {
        return new DirectExchange(CHAT_MESSAGE_EXCHANGE);
    }
    
    // 绑定队列和交换机
    @Bean
    public Binding chatMessageBinding() {
        return BindingBuilder.bind(chatMessageQueue()).to(chatMessageExchange()).with(CHAT_MESSAGE_ROUTING_KEY);
    }
}