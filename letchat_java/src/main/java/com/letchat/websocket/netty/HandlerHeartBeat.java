package com.letchat.websocket.netty;

import com.letchat.websocket.ChannelContextUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandlerHeartBeat extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                String userId = ChannelContextUtils.getUserId(ctx.channel());
                log.warn("WebSocket heartbeat timed out, userId={}", userId);
                ctx.close();
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
