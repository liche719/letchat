package com.letchat;

import com.alibaba.fastjson.JSON;
import com.letchat.config.AppConfig;
import com.letchat.config.RabbitMQConfig;
import com.letchat.entity.po.ChatMessage;
import com.letchat.utils.StringTools;
import com.letchat.websocket.netty.NettyWebSocketStarter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;

@Component("initRun")
@Slf4j
public class InitRun implements ApplicationRunner {

    @Resource
    private DataSource dataSource;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private NettyWebSocketStarter nettyWebSocketStarter;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private AppConfig appConfig;

    private static final ChatMessage chatMessage = new ChatMessage()
            .setMessageType(2)
            .setMessageContent("启动时测试消息111")
            .setSendUserId("U03372134858")
            .setSendUserNickName("超")
            .setSendTime(System.currentTimeMillis())
            .setContactId("U03590064702")
            .setSessionId(StringTools.getChatSessionId4User(new String[]{"U03372134858", "U03590064702"}))
            .setContactType(0);

    @Override
    public void run(ApplicationArguments args) {
        try {
            dataSource.getConnection();
            redisTemplate.opsForValue().get("test");
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_MESSAGE_EXCHANGE,
                    RabbitMQConfig.CHAT_MESSAGE_ROUTING_KEY,
                    JSON.toJSONString(chatMessage)
            );
            new Thread(nettyWebSocketStarter).start();
            log.info("服务启动成功,端口:{}", appConfig.getServerPort());
        } catch (SQLException e) {
            log.error("数据源获取失败", e);
        } catch (RedisConnectionFailureException e) {
            log.error("redis获取失败", e);
        } catch (AmqpConnectException e) {
            log.error("MQ连接失败", e);
        } catch (Exception e) {
            log.error("服务启动失败", e);
        }
    }
}
