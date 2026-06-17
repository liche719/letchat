package com.letchat.mq;

import com.alibaba.fastjson.JSON;
import com.letchat.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component("chatMessageProducer")
@Slf4j
public class ChatMessageProducer<T> {
    
    @Resource
    private RabbitTemplate rabbitTemplate;
    
    public void saveChatMessage(T t) {
        try {
            String message = JSON.toJSONString(t);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_MESSAGE_EXCHANGE,
                    RabbitMQConfig.CHAT_MESSAGE_ROUTING_KEY,
                    message
            );
            log.info("发送消息到RabbitMQ: {}", message);
        } catch (Exception e) {
            log.error("发送消息到RabbitMQ失败", e);
            // 可以考虑添加消息重试机制或日志记录
        }
    }
}