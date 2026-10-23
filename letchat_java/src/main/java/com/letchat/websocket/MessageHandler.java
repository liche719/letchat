package com.letchat.websocket;

import com.letchat.entity.dto.MessageSendDto;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

@Component("messageHandler")
@Slf4j
public class MessageHandler {

    private static final String topicName = "message.topic";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ChannelContextUtils channelContextUtils;


    @PostConstruct
    public void listenMessage() {
        RTopic topic = redissonClient.getTopic(topicName);
        topic.addListener(MessageSendDto.class, (MessageSendDto, message) -> {
            // 只记录投递维度的信息，不打印消息正文：聊天内容属于用户数据，
            // 落进日志盘/日志平台就等于多一份未授权副本。
            log.debug("广播消息 messageId={}, contactId={}, messageType={}",
                    message.getMessageId(), message.getContactId(), message.getMessageType());
            channelContextUtils.sendMessage(message);
        });
    }

    public void sendMessage(MessageSendDto message) {
        RTopic topic = redissonClient.getTopic(topicName);
        topic.publish(message);
    }

}
