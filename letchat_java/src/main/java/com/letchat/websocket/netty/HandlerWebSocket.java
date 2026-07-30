package com.letchat.websocket.netty;

import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.redis.RedisComponent;
import com.letchat.metrics.ChatMetrics;
import com.letchat.utils.StringTools;
import com.letchat.websocket.ChannelContextUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

@Component
@ChannelHandler.Sharable//允许多个实例，线程安全
@Slf4j
public class HandlerWebSocket extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private ChatMetrics chatMetrics;

    /**
     * 用户初始化
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("有新的连接加入...");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("有连接断开...");
        channelContextUtils.removeContext(ctx.channel());
        chatMetrics.webSocketDisconnected();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        Channel channel = ctx.channel();
        String userId = ChannelContextUtils.getUserId(channel);
        if (StringTools.isEmpty(userId)) {
            channel.close();
            return;
        }
        log.info("收到userId={}消息：{}", userId, textWebSocketFrame.text());
        redisComponent.saveHeartBeat(userId);
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete complete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String uri = complete.requestUri();
            String ticket = getTicket(uri);
            if (StringTools.isEmpty(ticket)) {
                ctx.channel().close();
                return;
            }
            TokenUserInfoDto tokenUserInfoDto = redisComponent.consumeWebSocketTicket(ticket);
            if (null == tokenUserInfoDto) {
                ctx.channel().close();
                return;
            }
            channelContextUtils.addContext(tokenUserInfoDto.getUserId(), ctx.channel());
            chatMetrics.webSocketConnected();
        }
    }

    String getTicket(String uri) {
        if (StringTools.isEmpty(uri)) {
            return null;
        }
        List<String> tickets = new QueryStringDecoder(uri).parameters().get("ticket");
        if (tickets == null || tickets.isEmpty()) {
            return null;
        }
        return tickets.get(0);
    }
}
