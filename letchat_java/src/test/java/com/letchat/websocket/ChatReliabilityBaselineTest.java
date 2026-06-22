package com.letchat.websocket;

import com.letchat.mq.ChatMessageRetryPolicy;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.local.LocalChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatReliabilityBaselineTest {

    @AfterEach
    void tearDown() {
        ChannelContextUtils.clearLocalContexts();
    }

    @Test
    void writesChatReliabilityBaselineMetrics() throws Exception {
        int users = 500;
        int workers = 32;
        String groupId = "G-BASELINE";

        ChannelContextUtils contextUtils = new ChannelContextUtils();
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users);
        List<LocalChannel> channels = new ArrayList<>();

        long startedNanos = System.nanoTime();
        for (int i = 0; i < users; i++) {
            LocalChannel channel = new LocalChannel();
            eventLoop.register(channel).syncUninterruptibly();
            channels.add(channel);
            String userId = "UB" + i;
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
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

        ChatMessageRetryPolicy retryPolicy = new ChatMessageRetryPolicy(3);
        Message retryMessage = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        int republishedRetries = 0;
        while (retryPolicy.canRetry(retryMessage)) {
            retryMessage = retryPolicy.buildRetryMessage(retryMessage);
            republishedRetries++;
        }

        int onlineUsers = contextUtils.getOnlineUserCount();
        int groupConnections = contextUtils.getGroupConnectionCount(groupId);
        assertEquals(users, onlineUsers);
        assertEquals(users, groupConnections);
        assertEquals(3, republishedRetries);

        Path output = Paths.get("target", "letchat-baseline", "chat-reliability-baseline.json");
        Files.createDirectories(output.getParent());
        String json = String.format(Locale.ROOT,
                "{%n" +
                        "  \"websocket-group-join\": {%n" +
                        "    \"users\": %d,%n" +
                        "    \"workers\": %d,%n" +
                        "    \"onlineUsers\": %d,%n" +
                        "    \"groupConnections\": %d,%n" +
                        "    \"elapsedMs\": %d%n" +
                        "  },%n" +
                        "  \"rabbitmq-retry-policy\": {%n" +
                        "    \"maxRetries\": %d,%n" +
                        "    \"republishedRetries\": %d,%n" +
                        "    \"finalAction\": \"dead-letter\"%n" +
                        "  }%n" +
                        "}%n",
                users,
                workers,
                onlineUsers,
                groupConnections,
                elapsedMs,
                retryPolicy.getRetryCount(retryMessage),
                republishedRetries
        );
        Files.write(output, json.getBytes(StandardCharsets.UTF_8));

        executor.shutdownNow();
        channels.forEach(LocalChannel::close);
        eventLoop.shutdownGracefully();
    }
}
