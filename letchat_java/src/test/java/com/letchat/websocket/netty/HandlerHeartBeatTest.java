package com.letchat.websocket.netty;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HandlerHeartBeatTest {

    @Test
    void closesConnectionAfterReaderIdleTimeout() {
        EmbeddedChannel channel = new EmbeddedChannel(new HandlerHeartBeat());
        channel.attr(AttributeKey.<String>valueOf("letchat.userId")).set("U1001");

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        channel.runPendingTasks();

        assertFalse(channel.isOpen());
    }
}
