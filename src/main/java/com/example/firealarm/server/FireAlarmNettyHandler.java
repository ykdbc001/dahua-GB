package com.example.firealarm.server;

import com.example.firealarm.protocol.ProtocolMode;
import com.example.firealarm.protocol.dahua.DahuaProtocolProcessor;
import com.example.firealarm.protocol.demo.DemoProtocolProcessor;
import com.example.firealarm.service.DahuaDeviceIdentityService;
import com.example.firealarm.service.DeviceChannelRegistry;
import com.example.firealarm.service.ProtocolModeService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class FireAlarmNettyHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(FireAlarmNettyHandler.class);

    private final ProtocolModeService protocolModeService;
    private final DemoProtocolProcessor demoProtocolProcessor;
    private final DahuaProtocolProcessor dahuaProtocolProcessor;
    private final DeviceChannelRegistry deviceChannelRegistry;
    private final DahuaDeviceIdentityService dahuaDeviceIdentityService;

    public FireAlarmNettyHandler(
            ProtocolModeService protocolModeService,
            DemoProtocolProcessor demoProtocolProcessor,
            DahuaProtocolProcessor dahuaProtocolProcessor,
            DeviceChannelRegistry deviceChannelRegistry,
            DahuaDeviceIdentityService dahuaDeviceIdentityService) {
        this.protocolModeService = protocolModeService;
        this.demoProtocolProcessor = demoProtocolProcessor;
        this.dahuaProtocolProcessor = dahuaProtocolProcessor;
        this.deviceChannelRegistry = deviceChannelRegistry;
        this.dahuaDeviceIdentityService = dahuaDeviceIdentityService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("设备连接：{}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("设备断开连接：{}", ctx.channel().remoteAddress());
        deviceChannelRegistry.removeIfChannel(ctx.channel());
        dahuaDeviceIdentityService.remove(ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof byte[]) {
            byte[] data = (byte[]) msg;
            if (protocolModeService.getMode() == ProtocolMode.DAHUA) {
                dahuaProtocolProcessor.processMessage(ctx, data);
            } else {
                demoProtocolProcessor.processMessage(ctx, data);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            String remoteAddress = ctx.channel().remoteAddress().toString();
            switch (event.state()) {
                case READER_IDLE:
                    log.warn("设备读取超时：{}", remoteAddress);
                    break;
                case ALL_IDLE:
                    log.warn("设备空闲超时，关闭连接：{}", remoteAddress);
                    ctx.close();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("消防消息处理异常：{}", cause.getMessage());
        ctx.close();
    }
}
