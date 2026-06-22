package com.letchat.websocket;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.local.LocalChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
