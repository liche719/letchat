package com.letchat.websocket.netty;

import com.letchat.config.AppConfig;
import com.letchat.utils.StringTools;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class NettyWebSocketStarter implements Runnable {

    private static EventLoopGroup bossGroup = new NioEventLoopGroup();

    private static EventLoopGroup workGroup = new NioEventLoopGroup();

    @Resource
    private AppConfig appConfig;

    @Resource
    private HandlerWebSocket handlerWebSocket;

    @PreDestroy
    public void close() {
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
    }

    @Override
    public void run() {
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup);
            serverBootstrap.channel(NioServerSocketChannel.class).handler(new LoggingHandler(LogLevel.DEBUG)).childHandler(new ChannelInitializer() {

                @Override
                protected void initChannel(Channel channel) throws Exception {
                    ChannelPipeline pipeline = channel.pipeline();
                    // 对http的支持，使用http的解码器和编码器
                    pipeline.addLast(new HttpServerCodec());
                    //聚合解码 httpRequest/httpContent/lastHttpContent到FullHttpRequest
                    //保证接收的http请求的完整性
                    pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                    // long readerIdleTime, 读超时时间 TODO 6000000->6
                    // long writerIdleTime, 写超时时间
                    // long allIdleTime, 所有类型超时时间
                    pipeline.addLast(new IdleStateHandler(6000000, 0, 0, TimeUnit.SECONDS));
                    pipeline.addLast(new HandlerHeartBeat());//自定义心跳处理器
                    //将http协议升级为ws协议
                    pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 64 * 1024, true, true, 10000L));
                    pipeline.addLast(handlerWebSocket);

                }
            });
            Integer wsPort = appConfig.getWsPort();
            String wsPortStr = System.getProperty("ws.port");
            if (!StringTools.isEmpty(wsPortStr)) {
                wsPort = Integer.parseInt(wsPortStr);
            }
            ChannelFuture channelFuture = serverBootstrap.bind(wsPort).sync();
            log.info("netty服务启动成功,端口:{}", wsPort);
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("启动netty失败", e);
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }

}

