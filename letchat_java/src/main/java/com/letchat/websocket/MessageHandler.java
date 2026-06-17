package com.letchat.websocket;

import com.letchat.entity.dto.MessageSendDto;
import com.letchat.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

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
            log.info("收到消息：{}", JsonUtils.convertObj2Json(message));
            channelContextUtils.sendMessage(message);
        });
    }

    public void sendMessage(MessageSendDto message) {
        RTopic topic = redissonClient.getTopic(topicName);
        topic.publish(message);
    }

}
