package com.letchat.service.impl;

import com.letchat.config.AppConfig;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.GroupAddOrRemoveEnum;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSession;
import com.letchat.entity.po.ChatSessionUser;
import com.letchat.entity.po.GroupInfo;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.query.ChatSessionQuery;
import com.letchat.entity.query.ChatSessionUserQuery;
import com.letchat.entity.query.GroupInfoQuery;
import com.letchat.entity.query.UserContactQuery;
import com.letchat.entity.query.UserInfoQuery;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatSessionMapper;
import com.letchat.mappers.ChatSessionUserMapper;
import com.letchat.mappers.GroupInfoMapper;
import com.letchat.mappers.UserContactMapper;
import com.letchat.mappers.UserInfoMapper;
import com.letchat.redis.RedisComponent;
import com.letchat.service.ChatSessionUserService;
import com.letchat.service.GroupInfoService;
import com.letchat.websocket.ChannelContextUtils;
import com.letchat.websocket.MessageHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupInfoServiceImplTest {

    @Mock private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;
    @Mock private UserContactMapper<UserContact, UserContactQuery> userContactMapper;
    @Mock private RedisComponent redisComponent;
    @Mock private AppConfig appConfig;
    @Mock private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;
    @Mock private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;
    @Mock private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;
    @Mock private MessageHandler messageHandler;
    @Mock private ChannelContextUtils channelContextUtils;
    @Mock private ChatSessionUserService chatSessionUserService;
    @Mock private UserContactServiceImpl userContactService;
    @Mock private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;
    @Mock private GroupInfoService groupInfoService;
    @InjectMocks private GroupInfoServiceImpl service;
    @TempDir Path tempDirectory;

    @Test
    void rejectsMemberChangesFromNonOwner() {
        GroupInfo group = new GroupInfo();
        group.setGroupId("G001");
        group.setGroupOwnerId("U_OWNER");
        when(groupInfoMapper.selectByGroupId("G001")).thenReturn(group);
        TokenUserInfoDto operator = new TokenUserInfoDto();
        operator.setUserId("U_OTHER");

        assertThrows(BusinessException.class, () -> service.addOrRemoveGroupUser(operator, "G001", "U_MEMBER", GroupAddOrRemoveEnum.REMOVE.getStatus()));

        verify(groupInfoService, never()).leaveGroup(any(), any(), any());
        verify(userContactService, never()).addContact(any(), any(), any(), any(), any());
    }

    @Test
    void ownerRemovalDelegatesToLeaveGroupWithRemovalEvent() {
        GroupInfo group = new GroupInfo();
        group.setGroupId("G001");
        group.setGroupOwnerId("U_OWNER");
        when(groupInfoMapper.selectByGroupId("G001")).thenReturn(group);
        TokenUserInfoDto owner = new TokenUserInfoDto();
        owner.setUserId("U_OWNER");

        service.addOrRemoveGroupUser(owner, "G001", "U_MEMBER", GroupAddOrRemoveEnum.REMOVE.getStatus());

        verify(groupInfoService).leaveGroup("U_MEMBER", "G001", MessageTypeEnum.REMOVE_GROUP);
    }

    @Test
    void groupOwnerCannotLeaveWithoutDissolving() {
        GroupInfo group = new GroupInfo();
        group.setGroupId("G001");
        group.setGroupOwnerId("U_OWNER");
        when(groupInfoMapper.selectByGroupId("G001")).thenReturn(group);

        assertThrows(BusinessException.class, () -> service.leaveGroup("U_OWNER", "G001", MessageTypeEnum.LEAVE_GROUP));
        verify(userContactMapper, never()).deleteByUserIdAndContactId(any(), any());
    }

    @Test
    void updateCanUseAvatarAsCoverWhenCoverIsNotProvided() throws Exception {
        GroupInfo existing = new GroupInfo();
        existing.setGroupId("G001");
        existing.setGroupOwnerId("U_OWNER");
        existing.setGroupName("原群名");
        GroupInfo update = new GroupInfo();
        update.setGroupId("G001");
        update.setGroupOwnerId("U_OWNER");
        update.setGroupName("新群名");
        when(groupInfoMapper.selectByGroupId("G001")).thenReturn(existing);
        when(appConfig.getProjectFolderPath()).thenReturn(tempDirectory);
        MockMultipartFile avatar = new MockMultipartFile("avatarFile", "group.png", "image/png", new byte[]{1, 2, 3});

        service.saveGroup(update, avatar, null);

        assertTrue(Files.exists(tempDirectory.resolve("file/avatar/G001.png")));
        assertTrue(Files.exists(tempDirectory.resolve("file/avatar/G001.png_cover.png")));
        verify(chatSessionUserService).updateContactName("G001", "新群名");
        verify(groupInfoMapper).updateByGroupId(eq(update), eq("G001"));
    }
}
