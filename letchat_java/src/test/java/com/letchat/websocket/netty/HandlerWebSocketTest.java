package com.letchat.websocket.netty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HandlerWebSocketTest {

    private final HandlerWebSocket handler = new HandlerWebSocket();

    @Test
    void extractsEncodedTokenAmongOtherQueryParameters() {
        assertEquals(
                "token/value",
                handler.getToken("/ws?client=web&token=token%2Fvalue&version=1")
        );
    }

    @Test
    void returnsNullWhenTokenIsMissing() {
        assertNull(handler.getToken("/ws?client=web"));
        assertNull(handler.getToken("/ws"));
    }
}
