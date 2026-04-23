package com.example.firealarm.protocol.demo;

import com.example.firealarm.model.FireAlarmEvent;
import com.example.firealarm.model.FireMessage;
import com.example.firealarm.protocol.FireProtocolParser;
import com.example.firealarm.service.DeviceChannelRegistry;
import com.example.firealarm.service.FireAlarmEventHandler;
import com.example.firealarm.service.FireAlarmNotificationService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demo：国标 26875.3 风格解析 + 命令+0x80 应答（与原 FireAlarmServer 行为一致）。
 */
@Component
public class DemoProtocolProcessor {

    private static final Logger log = LoggerFactory.getLogger(DemoProtocolProcessor.class);

    private final FireProtocolParser protocolParser;
    private final FireAlarmEventHandler eventHandler;
    private final FireAlarmNotificationService notificationService;
    private final DeviceChannelRegistry deviceChannelRegistry;

    public DemoProtocolProcessor(
            FireProtocolParser protocolParser,
            FireAlarmEventHandler eventHandler,
            FireAlarmNotificationService notificationService,
            DeviceChannelRegistry deviceChannelRegistry) {
        this.protocolParser = protocolParser;
        this.eventHandler = eventHandler;
        this.notificationService = notificationService;
        this.deviceChannelRegistry = deviceChannelRegistry;
    }

    public void processMessage(ChannelHandlerContext ctx, byte[] data) {
        try {
            FireMessage message = protocolParser.parseMessage(data);
            if (message == null) {
                log.error("[Demo] 解析消息失败");
                return;
            }

            String deviceAddress = message.getSourceAddress();
            deviceChannelRegistry.put(deviceAddress, ctx.channel());

            FireAlarmEvent event = eventHandler.handleMessage(message);
            if (event != null) {
                notificationService.notifyAlarmEvent(event);
            }

            sendResponse(ctx, message);
        } catch (Exception e) {
            log.error("[Demo] 处理消防消息异常", e);
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, FireMessage request) {
        try {
            byte[] response = protocolParser.buildMessage(
                    request.getTargetType(),
                    request.getTargetAddress(),
                    request.getSourceType(),
                    request.getSourceAddress(),
                    request.getSerialNumber(),
                    (byte) (request.getCommand() + 0x80),
                    new byte[]{0x00}
            );

            if (response != null) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
            }
        } catch (Exception e) {
            log.error("[Demo] 发送响应消息异常", e);
        }
    }
}
