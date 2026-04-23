package com.example.firealarm.server;

import com.example.firealarm.service.DeviceChannelRegistry;
import com.example.firealarm.service.ProtocolModeService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * 消防报警 TCP 服务（Netty）。组帧策略见 {@link ProtocolModeFrameDecoder}，业务见 {@link FireAlarmNettyHandler}。
 */
@Component
public class FireAlarmServer {

    private static final Logger log = LoggerFactory.getLogger(FireAlarmServer.class);

    @Value("${fire-alarm.server.port:9000}")
    private int port;

    @Value("${fire-alarm.server.read-timeout:60000}")
    private long readTimeout;

    @Value("${fire-alarm.server.idle-timeout:180000}")
    private long idleTimeout;

    @Autowired
    private ProtocolModeService protocolModeService;

    @Autowired
    private FireAlarmNettyHandler fireAlarmNettyHandler;

    @Autowired
    private DeviceChannelRegistry deviceChannelRegistry;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void start() {
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(
                                            readTimeout / 1000, 0, idleTimeout / 1000, TimeUnit.SECONDS))
                                    .addLast(new ProtocolModeFrameDecoder(protocolModeService))
                                    .addLast(fireAlarmNettyHandler);
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("消防报警服务器已启动，监听端口：{}（解析模式见 fire-alarm.protocol-mode / 首页切换）", port);
        } catch (Exception e) {
            log.error("启动消防报警服务器失败", e);
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            log.info("消防报警服务器已关闭");
        } catch (Exception e) {
            log.error("关闭消防报警服务器异常", e);
        }
    }

    public boolean sendMessage(String deviceAddress, byte[] data) {
        try {
            Channel channel = deviceChannelRegistry.get(deviceAddress);
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(Unpooled.wrappedBuffer(data));
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("发送消息异常", e);
            return false;
        }
    }
}
