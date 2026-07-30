package com.letchat.websocket.netty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HandlerWebSocketTest {

    private final HandlerWebSocket handler = new HandlerWebSocket();

    @Test
    void extractsEncodedTicketAmongOtherQueryParameters() {
        assertEquals(
                "ticket/value",
                handler.getTicket("/ws?client=web&ticket=ticket%2Fvalue&version=1")
        );
    }

    @Test
    void returnsNullWhenTicketIsMissing() {
        assertNull(handler.getTicket("/ws?client=web"));
        assertNull(handler.getTicket("/ws"));
    }
}
