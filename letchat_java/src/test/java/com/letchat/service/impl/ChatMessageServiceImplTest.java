package com.letchat.service.impl;

import com.letchat.config.AppConfig;
import com.letchat.entity.dto.SysSettingDto;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mq.ChatMessageProducer;
import com.letchat.redis.RedisComponent;
import com.letchat.utils.MessageIdGenerator;
import com.letchat.utils.StringTools;
import com.letchat.websocket.MessageHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    @Mock
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Mock
    private RedisComponent redisComponent;

    @Mock
    private ChatMessageProducer<ChatMessage> chatMessageProducer;

    @Mock
    private MessageHandler messageHandler;

    @Mock
    private MessageIdGenerator messageIdGenerator;

    @Mock
    private AppConfig appConfig;

    @TempDir
    Path tempDirectory;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

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
        assertEquals("send_time desc", query.getOrderBy());
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
    void assignsMessageIdBeforePublishingAsynchronously() {
        String userId = "U1001";
        String contactId = "U1002";
        when(redisComponent.getUserContactList(userId)).thenReturn(Collections.singletonList(contactId));
        when(messageIdGenerator.nextId()).thenReturn(123456789L);
        ChatMessage message = new ChatMessage()
                .setSendUserId(userId)
                .setSendUserNickName("Alice")
                .setContactId(contactId)
                .setMessageContent("hello")
                .setMessageType(2);

        MessageSendDto result = chatMessageService.saveMessage(message);

        assertEquals(123456789L, result.getMessageId());
        verify(chatMessageProducer).saveChatMessage(message);
        verify(messageHandler).sendMessage(result);
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
        when(appConfig.getProjectFolderPath()).thenReturn(tempDirectory);
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

        try (Stream<Path> paths = Files.walk(tempDirectory)) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().equals(messageId + ".png")));
        }
        verify(chatMessageMapper).updateByParam(any(ChatMessage.class), any(ChatMessageQuery.class));
    }
}
