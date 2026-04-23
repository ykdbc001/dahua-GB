package com.example.firealarm.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 将日志行推送到 /topic/logs（需配合 LogStompBridge 在 Spring 中注册）。
 */
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    protected void append(ILoggingEvent event) {
        String time = FMT.format(Instant.ofEpochMilli(event.getTimeStamp()));
        String logger = event.getLoggerName();
        if (logger != null && logger.length() > 36) {
            logger = "…" + logger.substring(logger.length() - 34);
        }
        String line = time + " " + event.getLevel() + " " + logger + " - " + event.getFormattedMessage();
        LogStompBridge.publishLogLine(line);
    }
}
