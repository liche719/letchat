package com.letchat.service.impl;

import com.letchat.mappers.ChatMessageInboxMapper;
import com.letchat.mappers.ChatMessageOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRetentionServiceImplTest {

    @Test
    void deletesInBatchesUntilBelowBatchSize() {
        ChatMessageOutboxMapper outbox = mock(ChatMessageOutboxMapper.class);
        ChatMessageInboxMapper inbox = mock(ChatMessageInboxMapper.class);
        ChatRetentionServiceImpl service = new ChatRetentionServiceImpl();
        ReflectionTestUtils.setField(service, "chatMessageOutboxMapper", outbox);
        ReflectionTestUtils.setField(service, "chatMessageInboxMapper", inbox);
        // 第一批满额（5000）说明可能还有，第二批 1200 不足额即停止
        when(outbox.deleteFinishedBefore(7, ChatRetentionServiceImpl.DELETE_BATCH_SIZE)).thenReturn(5000, 1200);
        when(inbox.deleteConsumedBefore(7, ChatRetentionServiceImpl.DELETE_BATCH_SIZE)).thenReturn(300);

        var result = service.purge(7);

        assertEquals(6200, result.outboxDeleted());
        assertEquals(300, result.inboxDeleted());
        verify(outbox, times(2)).deleteFinishedBefore(7, ChatRetentionServiceImpl.DELETE_BATCH_SIZE);
        verify(inbox, times(1)).deleteConsumedBefore(7, ChatRetentionServiceImpl.DELETE_BATCH_SIZE);
    }

    @Test
    void stopsAfterMaximumBatchesToKeepTheRunBounded() {
        ChatMessageOutboxMapper outbox = mock(ChatMessageOutboxMapper.class);
        ChatMessageInboxMapper inbox = mock(ChatMessageInboxMapper.class);
        ChatRetentionServiceImpl service = new ChatRetentionServiceImpl();
        ReflectionTestUtils.setField(service, "chatMessageOutboxMapper", outbox);
        ReflectionTestUtils.setField(service, "chatMessageInboxMapper", inbox);
        when(outbox.deleteFinishedBefore(7, ChatRetentionServiceImpl.DELETE_BATCH_SIZE)).thenReturn(5000);
        when(inbox.deleteConsumedBefore(7, ChatRetentionServiceImpl.DELETE_BATCH_SIZE)).thenReturn(0);

        var result = service.purge(7);

        assertEquals(5000 * ChatRetentionServiceImpl.MAX_BATCHES_PER_RUN, result.outboxDeleted());
        verify(outbox, times(ChatRetentionServiceImpl.MAX_BATCHES_PER_RUN))
                .deleteFinishedBefore(7, ChatRetentionServiceImpl.DELETE_BATCH_SIZE);
    }

    @Test
    void doesNothingWhenNothingIsEligible() {
        ChatMessageOutboxMapper outbox = mock(ChatMessageOutboxMapper.class);
        ChatMessageInboxMapper inbox = mock(ChatMessageInboxMapper.class);
        ChatRetentionServiceImpl service = new ChatRetentionServiceImpl();
        ReflectionTestUtils.setField(service, "chatMessageOutboxMapper", outbox);
        ReflectionTestUtils.setField(service, "chatMessageInboxMapper", inbox);

        var result = service.purge(30);

        assertEquals(0, result.outboxDeleted());
        assertEquals(0, result.inboxDeleted());
        verify(outbox, times(1)).deleteFinishedBefore(30, ChatRetentionServiceImpl.DELETE_BATCH_SIZE);
    }
}
