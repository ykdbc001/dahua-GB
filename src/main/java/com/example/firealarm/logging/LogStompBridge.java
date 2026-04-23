package com.example.firealarm.logging;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 供 Logback Appender 在 Spring 上下文就绪后向 STOMP 广播日志行（Appender 本身非 Spring Bean）。
 */
@Component
public class LogStompBridge {

    private static volatile SimpMessagingTemplate template;

    private final SimpMessagingTemplate messagingTemplate;

    public LogStompBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void register() {
        template = messagingTemplate;
    }

    @PreDestroy
    public void clear() {
        template = null;
    }

    public static void publishLogLine(String line) {
        SimpMessagingTemplate t = template;
        if (t == null || line == null) {
            return;
        }
        try {
            t.convertAndSend("/topic/logs", line);
        } catch (Exception ignored) {
            // 避免在 Appender 里再触发日志风暴
        }
    }
}
