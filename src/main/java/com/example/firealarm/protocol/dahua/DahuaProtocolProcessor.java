package com.example.firealarm.protocol.dahua;

import com.example.firealarm.model.FireAlarmEvent;
import com.example.firealarm.model.FireMessage;
import com.example.firealarm.service.DahuaFrameHistoryService;
import com.example.firealarm.service.DeviceChannelRegistry;
import com.example.firealarm.service.FireAlarmEventHandler;
import com.example.firealarm.service.FireAlarmNotificationService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大华 TCP 业务处理器。
 */
@Component
public class DahuaProtocolProcessor {

    private static final Logger log = LoggerFactory.getLogger(DahuaProtocolProcessor.class);

    private final DahuaProtocolParser protocolParser;
    private final FireAlarmEventHandler eventHandler;
    private final FireAlarmNotificationService notificationService;
    private final DeviceChannelRegistry deviceChannelRegistry;
    private final DahuaFrameHistoryService frameHistoryService;

    public DahuaProtocolProcessor(
            DahuaProtocolParser protocolParser,
            FireAlarmEventHandler eventHandler,
            FireAlarmNotificationService notificationService,
            DeviceChannelRegistry deviceChannelRegistry,
            DahuaFrameHistoryService frameHistoryService) {
        this.protocolParser = protocolParser;
        this.eventHandler = eventHandler;
        this.notificationService = notificationService;
        this.deviceChannelRegistry = deviceChannelRegistry;
        this.frameHistoryService = frameHistoryService;
    }

    public void processMessage(ChannelHandlerContext ctx, byte[] data) {
        try {
            FireMessage message = protocolParser.parseMessage(data);
            if (message == null) {
                log.warn("[大华] 解析失败（校验/长度/起止符），已丢弃本帧，未回确认");
                return;
            }

            log.info("[大华] 收帧 命令=0x{} 流水号={} 源地址={}",
                    String.format("%02X", message.getCommand() & 0xFF),
                    message.getSerialNumber(),
                    message.getSourceAddress());

            String deviceAddress = message.getSourceAddress();
            deviceChannelRegistry.put(deviceAddress, ctx.channel());
            frameHistoryService.record(buildHistoryRecord(message));

            FireAlarmEvent event = eventHandler.handleMessage(message);
            if (event != null) {
                notificationService.notifyAlarmEvent(event);
            }

            sendDahuaAck(ctx, message);
        } catch (Exception e) {
            log.error("[大华] 处理异常", e);
        }
    }

    private void sendDahuaAck(ChannelHandlerContext ctx, FireMessage request) {
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
                log.debug("[大华] 发确认 命令=0x{}", String.format("%02X", (request.getCommand() + 0x80) & 0xFF));
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
            }
        } catch (Exception e) {
            log.error("[大华] 发送确认异常", e);
        }
    }

    private Map<String, Object> buildHistoryRecord(FireMessage message) {
        LinkedHashMap<String, Object> record = new LinkedHashMap<String, Object>();
        record.put("sourceAddress", message.getSourceAddress());
        record.put("targetAddress", message.getTargetAddress());
        record.put("serialNumber", message.getSerialNumber());
        record.put("command", String.format("%02X", message.getCommand() & 0xFF));
        record.put("protocolDetails", message.getProtocolDetails());
        return record;
    }
}
