package com.letchat.service.impl;

import com.letchat.entity.dto.SysSettingDto;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSession;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.query.ChatSessionQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatMessageOutboxMapper;
import com.letchat.mappers.ChatMessageSequenceMapper;
import com.letchat.mappers.ChatMessageReceiptMapper;
import com.letchat.mappers.ChatSessionMapper;
import com.letchat.redis.RedisComponent;
import com.letchat.storage.FileStorage;
import com.letchat.utils.MessageIdGenerator;
import com.letchat.utils.StringTools;
import com.letchat.websocket.MessageHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    @Mock
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Mock
    private RedisComponent redisComponent;

    @Mock
    private ChatMessageOutboxMapper chatMessageOutboxMapper;

    @Mock
    private ChatMessageSequenceMapper chatMessageSequenceMapper;

    @Mock
    private ChatMessageReceiptMapper chatMessageReceiptMapper;

    @Mock
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

    @Mock
    private com.letchat.mappers.UserContactMapper<com.letchat.entity.po.UserContact,
            com.letchat.entity.query.UserContactQuery> userContactMapper;

    @Mock
    private MessageHandler messageHandler;

    @Mock
    private MessageIdGenerator messageIdGenerator;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    @Test
    void fallsBackToDatabaseAndRefillsCacheWhenContactCacheIsEmpty() {
        String userId = "U1001";
        com.letchat.entity.po.UserContact contact = new com.letchat.entity.po.UserContact();
        contact.setContactId("U1002");
        when(redisComponent.getUserContactList(userId)).thenReturn(Collections.emptyList());
        when(userContactMapper.selectList(any(com.letchat.entity.query.UserContactQuery.class)))
                .thenReturn(List.of(contact));

        List<String> contactIds = chatMessageService.contactIdsOf(userId);

        // Redis 被清空/丢数据时不能直接把用户判成无权限，必须回源并回填
        assertEquals(List.of("U1002"), contactIds);
        verify(redisComponent).clearUserContact(userId);
        verify(redisComponent).addUserContactBatch(userId, List.of("U1002"));
    }

    @Test
    void privateHistoryUsesIndexedSessionPagination() {
        String userId = "U1001";
        String contactId = "U1002";
        when(redisComponent.getUserContactList(userId)).thenReturn(Collections.singletonList(contactId));
        when(chatMessageMapper.selectCount(any(ChatMessageQuery.class))).thenReturn(25);
        when(chatMessageMapper.selectList(any(ChatMessageQuery.class))).thenReturn(Collections.emptyList());

        PaginationResultVO<ChatMessage> result = chatMessageService.loadChatMessages(userId, contactId, 2);

        ArgumentCaptor<ChatMessageQuery> queryCaptor = ArgumentCaptor.forClass(ChatMessageQuery.class);
        verify(chatMessageMapper).selectList(queryCaptor.capture());
        ChatMessageQuery query = queryCaptor.getValue();
        assertEquals(StringTools.getChatSessionId4User(new String[]{userId, contactId}), query.getSessionId());
        assertEquals("sequence_no desc", query.getOrderBy());
        assertEquals(20, query.getPageSize());
        assertEquals(2, result.getPageNo());
        assertEquals(2, result.getPageTotal());
        assertEquals(25, result.getTotalCount());
    }

    @Test
    void historyRejectsUsersOutsideTheConversation() {
        when(redisComponent.getUserContactList("U1001")).thenReturn(Collections.emptyList());

        assertThrows(
                BusinessException.class,
                () -> chatMessageService.loadChatMessages("U1001", "U1002", 1)
        );
    }

    @Test
    void offlineSyncUsesMonotonicSequenceCursorAndCapsBatchSize() {
        String userId = "U1001";
        String contactId = "U1002";
        when(redisComponent.getUserContactList(userId)).thenReturn(List.of(contactId));
        when(chatMessageMapper.selectAfterSequence(any(String.class), anyLong(), anyInt()))
                .thenReturn(List.of(new ChatMessage().setSequenceNo(101L)));

        List<ChatMessage> result = chatMessageService.syncMessages(userId, contactId, 100L, 10_000);

        assertEquals(101L, result.getFirst().getSequenceNo());
        verify(chatMessageMapper).selectAfterSequence(
                StringTools.getChatSessionId4User(new String[]{userId, contactId}),
                100L,
                500
        );
    }

    @Test
    void deliveryAndReadAcknowledgementsAreMonotonicMapperOperations() {
        String userId = "U1001";
        String contactId = "U1002";
        String sessionId = StringTools.getChatSessionId4User(new String[]{userId, contactId});
        when(redisComponent.getUserContactList(userId)).thenReturn(List.of(contactId));

        chatMessageService.acknowledgeDelivered(userId, contactId, 9L);
        chatMessageService.acknowledgeRead(userId, contactId, 10L);

        verify(chatMessageReceiptMapper).acknowledgeDelivered(userId, sessionId, 9L);
        verify(chatMessageReceiptMapper).acknowledgeRead(userId, sessionId, 10L);
    }

    @Test
    void assignsMessageIdBeforePublishingAsynchronously() {
        String userId = "U1001";
        String contactId = "U1002";
        when(redisComponent.getUserContactList(userId)).thenReturn(Collections.singletonList(contactId));
        when(messageIdGenerator.nextId()).thenReturn(123456789L);
        when(chatMessageSequenceMapper.lastAllocated()).thenReturn(1L);
        ChatMessage message = new ChatMessage()
                .setSendUserId(userId)
                .setSendUserNickName("Alice")
                .setContactId(contactId)
                .setMessageContent("hello")
                .setMessageType(2);

        MessageSendDto result = chatMessageService.saveMessage(message);

        assertEquals(123456789L, result.getMessageId());
        verify(chatMessageOutboxMapper).insert(any());
        verify(chatMessageSequenceMapper).allocateNext(StringTools.getChatSessionId4User(new String[]{userId, contactId}));
        verify(chatMessageMapper).insertMessage(message);
        org.mockito.Mockito.verifyNoInteractions(messageHandler);
    }

    @Test
    void retryWithSameClientMessageIdReturnsExistingMessageWithoutNewOutboxEvent() {
        String userId = "U1001";
        String contactId = "U1002";
        ChatMessage existing = new ChatMessage()
                .setMessageId(99L)
                .setClientMessageId("client-1")
                .setSendUserId(userId)
                .setContactId(contactId)
                .setMessageType(2)
                .setMessageContent("hello")
                .setSequenceNo(7L);
        when(redisComponent.getUserContactList(userId)).thenReturn(List.of(contactId));
        when(chatMessageMapper.selectByClientMessageId("client-1")).thenReturn(existing);
        ChatMessage retry = new ChatMessage()
                .setClientMessageId("client-1")
                .setSendUserId(userId)
                .setContactId(contactId)
                .setMessageType(2)
                .setMessageContent("hello");

        MessageSendDto result = chatMessageService.saveMessage(retry);

        assertEquals(99L, result.getMessageId());
        assertEquals(7L, result.getSequenceNo());
        verify(chatMessageSequenceMapper, never()).allocateNext(any(String.class));
        verify(chatMessageOutboxMapper, never()).insert(any());
    }

    @Test
    void storesUploadedMediaUnderAbsoluteProjectFolder() throws Exception {
        long messageId = 123456789L;
        ChatMessage persistedMessage = new ChatMessage()
                .setMessageId(messageId)
                .setSendUserId("U1001")
                .setContactId("U1002")
                .setSendTime(System.currentTimeMillis());
        when(chatMessageMapper.selectByMessageId(messageId)).thenReturn(persistedMessage);
        when(redisComponent.getSysSetting()).thenReturn(new SysSettingDto());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        MockMultipartFile cover = new MockMultipartFile(
                "coverFile",
                "sample.png",
                "image/png",
                new byte[]{4, 5, 6}
        );

        chatMessageService.saveMessageFile("U1001", messageId, file, cover);

        verify(fileStorage, org.mockito.Mockito.times(2))
                .put(any(String.class), any(), anyLong(), any());
        verify(chatMessageMapper).updateByParam(any(ChatMessage.class), any(ChatMessageQuery.class));
        ArgumentCaptor<MessageSendDto> messageCaptor = ArgumentCaptor.forClass(MessageSendDto.class);
        verify(messageHandler).sendMessage(messageCaptor.capture());
        assertEquals(messageId, messageCaptor.getValue().getMessageId());
        assertEquals("U1001", messageCaptor.getValue().getSendUserId());
        assertEquals(MessageTypeEnum.FILE_UPLOAD.getType(), messageCaptor.getValue().getMessageType());
    }
}
