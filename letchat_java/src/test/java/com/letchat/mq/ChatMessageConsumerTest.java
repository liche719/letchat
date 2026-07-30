package com.letchat.mq;

import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.po.ChatMessage;
import com.letchat.mappers.ChatMessageInboxMapper;
import com.letchat.websocket.MessageHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageConsumerTest {

    @Test
    @SuppressWarnings("unchecked")
    void duplicateBrokerDeliveryProducesOneWebSocketNotification() {
        ChatMessageInboxMapper inbox = mock(ChatMessageInboxMapper.class);
        MessageHandler messageHandler = mock(MessageHandler.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> ((TransactionCallback<Boolean>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any(TransactionCallback.class));
        when(inbox.insertIfAbsent("client-1")).thenReturn(1, 0);
        ChatMessageConsumer consumer = new ChatMessageConsumer();
        ReflectionTestUtils.setField(consumer, "chatMessageInboxMapper", inbox);
        ReflectionTestUtils.setField(consumer, "messageHandler", messageHandler);
        ReflectionTestUtils.setField(consumer, "transactionTemplate", transactionTemplate);
        ChatMessage message = new ChatMessage()
                .setMessageId(1L)
                .setClientMessageId("client-1")
                .setMessageContent("hello");

        assertTrue(consumer.deliverIdempotently(message));
        assertFalse(consumer.deliverIdempotently(message));

        verify(messageHandler, times(1)).sendMessage(any(MessageSendDto.class));
    }
}
