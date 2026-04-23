package com.example.firealarm.service;

import com.example.firealarm.constant.FireProtocolConstant;
import com.example.firealarm.dto.SystemStatusDto;
import com.example.firealarm.model.FireAlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 消防报警事件通知服务
 */
@Service
public class FireAlarmNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FireAlarmNotificationService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 存储当前活跃的报警事件
    private final ConcurrentMap<String, FireAlarmEvent> activeAlarms = new ConcurrentHashMap<>();

    /**
     * 处理并通知报警事件
     *
     * @param event 报警事件
     */
    public void notifyAlarmEvent(FireAlarmEvent event) {
        if (event == null) {
            return;
        }

        try {
            // 根据事件类型进行处理
            switch (event.getEventType()) {
                case FireProtocolConstant.SystemStatus.FIRE_ALARM:
                    handleFireAlarm(event);
                    break;
                case FireProtocolConstant.SystemStatus.FAULT:
                    handleFault(event);
                    break;
                case FireProtocolConstant.SystemStatus.NORMAL:
                    handleNormal(event);
                    break;
                default:
                    // 其他类型事件直接通知
                    sendEventNotification(event);
                    break;
            }
        } catch (Exception e) {
            log.error("通知报警事件异常", e);
        } finally {
            broadcastSystemStatus();
        }
    }

    /**
     * 按当前活跃事件统计首页汇总，并推送到 /topic/system-status
     */
    public SystemStatusDto getSystemStatus() {
        List<FireAlarmEvent> activeAlarms = getActiveAlarms();
        long fireAlarmCount = activeAlarms.stream()
                .filter(e -> e.getEventType() == FireProtocolConstant.SystemStatus.FIRE_ALARM)
                .count();
        long faultCount = activeAlarms.stream()
                .filter(e -> e.getEventType() == FireProtocolConstant.SystemStatus.FAULT)
                .count();
        long otherCount = activeAlarms.stream()
                .filter(e -> e.getEventType() != FireProtocolConstant.SystemStatus.FIRE_ALARM
                        && e.getEventType() != FireProtocolConstant.SystemStatus.FAULT)
                .count();
        String status = fireAlarmCount > 0 ? "火警" : (faultCount > 0 ? "故障" : "正常");
        return new SystemStatusDto(activeAlarms.size(), fireAlarmCount, faultCount, otherCount, status);
    }

    private void broadcastSystemStatus() {
        try {
            messagingTemplate.convertAndSend("/topic/system-status", getSystemStatus());
        } catch (Exception e) {
            log.error("广播系统状态异常", e);
        }
    }

    /**
     * 处理火警事件
     */
    private void handleFireAlarm(FireAlarmEvent event) {
        // 生成唯一键：设备地址 + 部件类型 + 部件地址
        String key = generateEventKey(event);

        // 添加到活跃报警列表
        activeAlarms.put(key, event);

        // 发送火警通知
        sendEventNotification(event);

        // 记录日志
        log.warn("火警事件：{}", event.getDescription());
    }

    /**
     * 处理故障事件
     */
    private void handleFault(FireAlarmEvent event) {
        // 生成唯一键
        String key = generateEventKey(event);

        // 添加到活跃报警列表
        activeAlarms.put(key, event);

        // 发送故障通知
        sendEventNotification(event);

        // 记录日志
        log.warn("故障事件：{}", event.getDescription());
    }

    /**
     * 处理恢复正常事件
     */
    private void handleNormal(FireAlarmEvent event) {
        // 生成唯一键
        String key = generateEventKey(event);

        // 从活跃报警列表中移除
        FireAlarmEvent previousEvent = activeAlarms.remove(key);

        // 如果之前有报警，发送恢复通知
        if (previousEvent != null) {
            sendEventNotification(event);
            log.info("事件恢复正常：{}", event.getDescription());
        }
    }

    /**
     * 生成事件唯一键
     */
    private String generateEventKey(FireAlarmEvent event) {
        return event.getDeviceAddress() + "-" + 
               event.getComponentType() + "-" + 
               event.getComponentAddress();
    }

    /**
     * 发送事件通知到WebSocket
     */
    private void sendEventNotification(FireAlarmEvent event) {
        try {
            // 发送到WebSocket主题
            messagingTemplate.convertAndSend("/topic/alarms", event);
        } catch (Exception e) {
            log.error("发送WebSocket通知异常", e);
        }
    }

    /**
     * 获取当前所有活跃报警
     */
    public List<FireAlarmEvent> getActiveAlarms() {
        return new ArrayList<>(activeAlarms.values());
    }
}