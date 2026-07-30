package com.letchat.websocket.netty;

import com.letchat.config.AppConfig;
import com.letchat.utils.StringTools;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class NettyWebSocketStarter implements SmartLifecycle {

    @Resource
    private AppConfig appConfig;

    @Resource
    private HandlerWebSocket handlerWebSocket;

    private volatile boolean running;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup businessGroup;
    private Channel serverChannel;

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        int wsPort = resolveWsPort();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        businessGroup = new DefaultEventExecutorGroup(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                runnable -> {
                    Thread thread = new Thread(runnable, "letchat-ws-business");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                            pipeline.addLast(new IdleStateHandler(
                                    appConfig.getWsReadIdleSeconds(),
                                    0,
                                    0,
                                    TimeUnit.SECONDS
                            ));
                            pipeline.addLast(new HandlerHeartBeat());
                            pipeline.addLast(new WebSocketServerProtocolHandler(
                                    "/ws",
                                    null,
                                    true,
                                    64 * 1024,
                                    true,
                                    true,
                                    10000L
                            ));
                            pipeline.addLast(businessGroup, handlerWebSocket);
                        }
                    });

            ChannelFuture bindFuture = serverBootstrap.bind(wsPort).syncUninterruptibly();
            if (!bindFuture.isSuccess()) {
                throw new IllegalStateException("Failed to bind WebSocket port " + wsPort, bindFuture.cause());
            }
            serverChannel = bindFuture.channel();
            running = true;
            log.info("Netty WebSocket server started on port {}", wsPort);
        } catch (RuntimeException e) {
            shutdownEventLoops();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (!running && bossGroup == null && workerGroup == null) {
            return;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        shutdownEventLoops();
        running = false;
        log.info("Netty WebSocket server stopped");
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private int resolveWsPort() {
        String wsPort = System.getProperty("ws.port");
        return StringTools.isEmpty(wsPort) ? appConfig.getWsPort() : Integer.parseInt(wsPort);
    }

    private void shutdownEventLoops() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup = null;
        }
        if (businessGroup != null) {
            businessGroup.shutdownGracefully().syncUninterruptibly();
            businessGroup = null;
        }
    }
}
