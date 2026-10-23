package com.letchat.websocket;

import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSessionUser;
import com.letchat.entity.po.UserContactApply;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.query.ChatSessionUserQuery;
import com.letchat.entity.query.UserContactApplyQuery;
import com.letchat.entity.query.UserInfoQuery;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatSessionUserMapper;
import com.letchat.mappers.UserContactApplyMapper;
import com.letchat.mappers.UserInfoMapper;
import com.letchat.redis.RedisComponent;
import com.letchat.utils.JsonUtils;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelContextUtilsConcurrencyTest {

    @AfterEach
    void tearDown() {
        ChannelContextUtils.clearLocalContexts();
    }

    @Test
    void concurrentGroupJoinKeepsEveryChannelInOneGroup() throws Exception {
        ChannelContextUtils contextUtils = new ChannelContextUtils();
        String groupId = "G-LOAD";
        int users = 200;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users);
        List<LocalChannel> channels = new ArrayList<>();

        for (int i = 0; i < users; i++) {
            LocalChannel channel = new LocalChannel();
            eventLoop.register(channel).syncUninterruptibly();
            channels.add(channel);
            String userId = "U" + i;
            contextUtils.bindUserChannel(userId, channel);
            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    contextUtils.addUser2Group(userId, groupId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(users, contextUtils.getOnlineUserCount());
        assertEquals(users, contextUtils.getGroupConnectionCount(groupId));
        executor.shutdownNow();
        channels.forEach(LocalChannel::close);
        eventLoop.shutdownGracefully();
    }

    @Test
    void staleChannelCloseDoesNotRemoveLatestConnection() {
        ChannelContextUtils contextUtils = new ChannelContextUtils();
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        LocalChannel first = new LocalChannel();
        LocalChannel second = new LocalChannel();
        eventLoop.register(first).syncUninterruptibly();
        eventLoop.register(second).syncUninterruptibly();

        contextUtils.bindUserChannel("U1001", first);
        contextUtils.bindUserChannel("U1001", second);
        contextUtils.removeContext(first);

        assertEquals(1, contextUtils.getOnlineUserCount());
        assertEquals("U1001", ChannelContextUtils.getUserId(second));
        first.close();
        second.close();
        eventLoop.shutdownGracefully();
    }

    @Test
    void dissolvingGroupDoesNotCloseMemberConnections() {
        ChannelContextUtils contextUtils = new ChannelContextUtils();
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        LocalChannel first = new LocalChannel();
        LocalChannel second = new LocalChannel();
        eventLoop.register(first).syncUninterruptibly();
        eventLoop.register(second).syncUninterruptibly();

        contextUtils.bindUserChannel("U1001", first);
        contextUtils.bindUserChannel("U1002", second);
        contextUtils.addUser2Group("U1001", "G1001");
        contextUtils.addUser2Group("U1002", "G1001");

        contextUtils.removeGroup("G1001");

        assertEquals(0, contextUtils.getGroupConnectionCount("G1001"));
        assertTrue(first.isOpen());
        assertTrue(second.isOpen());
        assertFalse(first.closeFuture().isDone());
        first.close();
        second.close();
        eventLoop.shutdownGracefully();
    }

    @Test
    void privateDeliveryDoesNotMutateMessageTarget() {
        ChannelContextUtils contextUtils = new ChannelContextUtils();
        EmbeddedChannel channel = new EmbeddedChannel();
        contextUtils.bindUserChannel("U1002", channel);

        MessageSendDto message = new MessageSendDto();
        message.setMessageType(2);
        message.setContactId("U1002");
        message.setSendUserId("U1001");
        message.setSendUserNickName("Alice");
        message.setMessageContent("hello");

        contextUtils.sendMsg(message, "U1002");

        TextWebSocketFrame frame = channel.readOutbound();
        MessageSendDto delivered = JsonUtils.convertJson2Obj(frame.text(), MessageSendDto.class);
        assertEquals("U1001", delivered.getContactId());
        assertEquals("U1002", message.getContactId());
        assertEquals("U1001", message.getSendUserId());
        channel.close();
    }

    @Test
    void websocketInitCountsApplicationsReceivedByUserWithoutContacts() {
        ChannelContextUtils contextUtils = new ChannelContextUtils();
        UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper = mock(UserInfoMapper.class);
        ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> sessionUserMapper = mock(ChatSessionUserMapper.class);
        ChatMessageMapper<ChatMessage, ChatMessageQuery> messageMapper = mock(ChatMessageMapper.class);
        UserContactApplyMapper<UserContactApply, UserContactApplyQuery> applyMapper = mock(UserContactApplyMapper.class);
        RedisComponent redisComponent = mock(RedisComponent.class);

        when(redisComponent.getUserContactList("U1001")).thenReturn(null);
        when(userInfoMapper.selectByUserId("U1001")).thenReturn(new UserInfo());
        when(sessionUserMapper.selectList(any(ChatSessionUserQuery.class))).thenReturn(List.of());
        when(messageMapper.selectList(any(ChatMessageQuery.class))).thenReturn(List.of());
        when(applyMapper.selectCount(any(UserContactApplyQuery.class))).thenReturn(2);

        ReflectionTestUtils.setField(contextUtils, "userInfoMapper", userInfoMapper);
        ReflectionTestUtils.setField(contextUtils, "chatSessionUserMapper", sessionUserMapper);
        ReflectionTestUtils.setField(contextUtils, "chatMessageMapper", messageMapper);
        ReflectionTestUtils.setField(contextUtils, "userContactApplyMapper", applyMapper);
        ReflectionTestUtils.setField(contextUtils, "redisComponent", redisComponent);

        EmbeddedChannel channel = new EmbeddedChannel();
        contextUtils.addContext("U1001", channel);

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(UserContactApplyQuery.class);
        verify(applyMapper).selectCount(queryCaptor.capture());
        assertEquals("U1001", queryCaptor.getValue().getReceiveUserId());
        assertNull(queryCaptor.getValue().getApplyUserId());
        channel.finishAndReleaseAll();
    }
}
