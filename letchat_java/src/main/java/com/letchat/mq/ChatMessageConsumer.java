package com.letchat.mq;

import com.alibaba.fastjson.JSON;
import com.letchat.config.RabbitMQConfig;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.MessageStatusEnum;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.entity.enums.UserContactTypeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSession;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatSessionMapper;
import com.letchat.redis.RedisComponent;
import com.letchat.utils.StringTools;
import com.letchat.websocket.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.lang.Thread.sleep;

@Component("chatMessageConsumer")
@Slf4j
public class ChatMessageConsumer {

    @Resource
    private ChatMessageMapper<ChatMessage, com.letchat.entity.query.ChatMessageQuery> chatMessageMapper;

    @Resource
    private ChatSessionMapper<ChatSession, com.letchat.entity.query.ChatSessionQuery> chatSessionMapper;


    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE)
    public void processChatMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("从RabbitMQ接收聊天消息: {}", message);

        try {
            ChatMessage chatMessage = getAndCheckReceive(message, channel, deliveryTag);
            if (chatMessage == null) {
                return;
            }

            // 异步处理数据库操作
            saveMessageAsync(chatMessage);

            // 手动确认消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理聊天消息失败: {}", message, e);
            // 处理失败，拒绝消息并不重新入队
            try {
                sleep(5000);
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("确认消息失败", ex);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static ChatMessage getAndCheckReceive(String message, Channel channel, long deliveryTag) throws IOException {
        // 检查消息是否为空
        if (message == null || message.trim().isEmpty()) {
            log.warn("接收到空消息");
            channel.basicAck(deliveryTag, false);
            return null;
        }
        // 检查是否为有效的JSON格式
        String trimmedMessage = message.trim();
        if (!trimmedMessage.startsWith("{")) {
            log.error("消息格式非JSON,消息: {}", message);
            channel.basicNack(deliveryTag, false, false);
            return null;
        }
        // 解析消息
        ChatMessage chatMessage = null;
        try {
            chatMessage = JSON.parseObject(message, ChatMessage.class);
            if (chatMessage == null) {
                log.warn("解析消息结果为null,消息: {}", message);
                channel.basicAck(deliveryTag, false);
                return null;
            }
        } catch (Exception e) {
            log.error("解析消息失败,消息: {}", message, e);
            channel.basicNack(deliveryTag, false, false);
            return null;
        }
        return chatMessage;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMessageAsync(ChatMessage chatMessage) {
        try {
            //更新会话
            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(chatMessage.getSessionId());
            chatSession.setLastMessage(chatMessage.getMessageContent());
            if (UserContactTypeEnum.GROUP == UserContactTypeEnum.getByPrefix(chatMessage.getContactId())) {
                chatSession.setLastMessage(chatMessage.getSendUserNickName() + "：" + chatMessage.getMessageContent());
            }
            chatSession.setLastReceiveTime(chatMessage.getSendTime());
            chatSessionMapper.updateBySessionId(chatSession, chatSession.getSessionId());

            chatMessageMapper.insertOrUpdate(chatMessage);

            log.info("异步保存数据库消息成功: {}", chatMessage.getMessageId());
        } catch (Exception e) {
            log.error("异步保存数据库消息失败", e);
            throw e;
        }
    }
}