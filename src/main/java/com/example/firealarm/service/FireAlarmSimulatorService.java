package com.example.firealarm.service;

import com.example.firealarm.constant.FireProtocolConstant;
import com.example.firealarm.protocol.FireProtocolParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消防设备模拟器（TCP 客户端），可通过 API / 首页开关启停。
 */
@Service
public class FireAlarmSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(FireAlarmSimulatorService.class);

    @Value("${fire-alarm.server.port:9000}")
    private int serverPort;

    @Autowired
    private FireProtocolParser protocolParser;

    private final Random random = new Random();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();

    private volatile ScheduledExecutorService scheduler;
    private volatile Thread receiveThread;
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private int serialNumber = 1;

    /** 火警恢复须与火警使用相同部件类型与地址，否则服务端无法从 activeAlarms 移除 */
    private volatile byte pendingFireComponentType;
    private final byte[] pendingFireAddress = new byte[6];

    private volatile byte pendingFaultComponentType;
    private final byte[] pendingFaultAddress = new byte[6];

    private final byte[] componentTypes = {
            FireProtocolConstant.ComponentType.FIRE_DETECTOR,
            FireProtocolConstant.ComponentType.MANUAL_ALARM_BUTTON,
            FireProtocolConstant.ComponentType.HYDRANT_BUTTON,
            FireProtocolConstant.ComponentType.DETECTION_CIRCUIT,
            FireProtocolConstant.ComponentType.FIRE_DISPLAY_PANEL,
            FireProtocolConstant.ComponentType.HEAT_DETECTOR,
            FireProtocolConstant.ComponentType.SMOKE_DETECTOR,
            FireProtocolConstant.ComponentType.COMPOSITE_FIRE_DETECTOR,
            FireProtocolConstant.ComponentType.UV_FLAME_DETECTOR,
            FireProtocolConstant.ComponentType.IR_FLAME_DETECTOR,
            FireProtocolConstant.ComponentType.GAS_DETECTOR,
            FireProtocolConstant.ComponentType.FIRE_PUMP,
            FireProtocolConstant.ComponentType.SPRINKLER_PUMP,
            FireProtocolConstant.ComponentType.WATER_FLOW_INDICATOR,
            FireProtocolConstant.ComponentType.SIGNAL_VALVE,
            FireProtocolConstant.ComponentType.ALARM_VALVE,
            FireProtocolConstant.ComponentType.PRESSURE_SWITCH,
            FireProtocolConstant.ComponentType.FIRE_DOOR,
            FireProtocolConstant.ComponentType.FIRE_VALVE,
            FireProtocolConstant.ComponentType.SMOKE_EXHAUST_FAN,
            FireProtocolConstant.ComponentType.FIRE_DOOR_MONITOR,
            FireProtocolConstant.ComponentType.ALARM_DEVICE
    };

    private final byte deviceType = FireProtocolConstant.ControlUnitType.FIRE_ALARM_SYSTEM;
    private final String deviceAddress = "010203040506";
    private final byte serverType = 0x00;
    private final String serverAddress = "000000000000";

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        scheduler = Executors.newScheduledThreadPool(2);
        scheduledTasks.clear();
        scheduledTasks.add(scheduler.schedule(this::startSimulatorInternal, 2, TimeUnit.SECONDS));
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        for (ScheduledFuture<?> f : scheduledTasks) {
            f.cancel(false);
        }
        scheduledTasks.clear();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        closeConnection();
        log.info("模拟器已停止");
    }

    private void startSimulatorInternal() {
        if (!running.get()) {
            return;
        }
        try {
            socket = new Socket("localhost", serverPort);
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

            log.info("模拟器已连接到服务器 localhost:{}", serverPort);

            receiveThread = new Thread(this::receiveMessages, "fire-simulator-recv");
            receiveThread.setDaemon(true);
            receiveThread.start();

            sendSystemStatus(FireProtocolConstant.SystemStatus.NORMAL);

            scheduledTasks.add(scheduler.scheduleAtFixedRate(() -> {
                if (!running.get()) {
                    return;
                }
                try {
                    sendSystemStatus(FireProtocolConstant.SystemStatus.NORMAL);
                } catch (Exception e) {
                    log.error("发送正常状态异常", e);
                }
            }, 30, 30, TimeUnit.SECONDS));

            scheduledTasks.add(scheduler.scheduleAtFixedRate(() -> {
                if (!running.get()) {
                    return;
                }
                try {
                    if (random.nextInt(10) < 4) {
                        if (random.nextBoolean()) {
                            sendFireAlarm();
                            scheduledTasks.add(scheduler.schedule(this::sendFireAlarmRestore, 10, TimeUnit.SECONDS));
                        } else {
                            sendFault();
                            scheduledTasks.add(scheduler.schedule(this::sendFaultRestore, 15, TimeUnit.SECONDS));
                        }
                    }
                } catch (Exception e) {
                    log.error("发送随机事件异常", e);
                }
            }, 15, 45, TimeUnit.SECONDS));

        } catch (Exception e) {
            log.error("启动模拟器异常", e);
            running.set(false);
        }
    }

    private void receiveMessages() {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while (running.get() && (bytesRead = inputStream.read(buffer)) != -1) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                log.info("接收到服务器响应: {}", bytesToHex(data));
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("接收消息异常", e);
            }
        } finally {
            if (running.get()) {
                log.warn("模拟器接收线程结束，连接已断开");
            }
        }
    }

    private void sendSystemStatus(byte status) {
        try {
            if (outputStream == null) {
                return;
            }
            byte[] data = new byte[13];
            data[0] = status;
            for (int i = 0; i < 6; i++) {
                data[1 + i] = (byte) (0x01 + i);
            }
            LocalDateTime now = LocalDateTime.now();
            data[7] = (byte) (now.getYear() - 2000);
            data[8] = (byte) now.getMonthValue();
            data[9] = (byte) now.getDayOfMonth();
            data[10] = (byte) now.getHour();
            data[11] = (byte) now.getMinute();
            data[12] = (byte) now.getSecond();

            byte[] message = protocolParser.buildMessage(
                    deviceType,
                    deviceAddress,
                    serverType,
                    serverAddress,
                    serialNumber++,
                    FireProtocolConstant.CommandType.UPLOAD_SYSTEM_STATUS,
                    data
            );

            outputStream.write(message);
            outputStream.flush();

            log.info("发送系统状态: {}", status == 0 ? "正常" : status == 1 ? "火警" : status == 2 ? "故障" : "其他");
        } catch (Exception e) {
            log.error("发送系统状态异常", e);
        }
    }

    private void sendFireAlarm() {
        try {
            if (outputStream == null) {
                return;
            }
            byte componentType = componentTypes[random.nextInt(componentTypes.length)];
            pendingFireComponentType = componentType;
            for (int i = 0; i < 6; i++) {
                pendingFireAddress[i] = (byte) random.nextInt(256);
            }

            byte[] data = buildComponentPayload(componentType, FireProtocolConstant.ComponentStatus.FIRE_ALARM, pendingFireAddress);
            byte[] message = protocolParser.buildMessage(
                    deviceType, deviceAddress, serverType, serverAddress,
                    serialNumber++, FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS, data);

            outputStream.write(message);
            outputStream.flush();
            sendSystemStatus(FireProtocolConstant.SystemStatus.FIRE_ALARM);
            log.info("发送火警事件: 部件类型={}", toUnsigned(componentType));
        } catch (Exception e) {
            log.error("发送火警事件异常", e);
        }
    }

    private void sendFireAlarmRestore() {
        if (!running.get()) {
            return;
        }
        try {
            if (outputStream == null) {
                return;
            }
            byte[] data = buildComponentPayload(pendingFireComponentType, FireProtocolConstant.ComponentStatus.NORMAL, pendingFireAddress);
            byte[] message = protocolParser.buildMessage(
                    deviceType, deviceAddress, serverType, serverAddress,
                    serialNumber++, FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS, data);

            outputStream.write(message);
            outputStream.flush();
            sendSystemStatus(FireProtocolConstant.SystemStatus.NORMAL);
            log.info("发送火警恢复事件: 部件类型={}", toUnsigned(pendingFireComponentType));
        } catch (Exception e) {
            log.error("发送火警恢复事件异常", e);
        }
    }

    private void sendFault() {
        try {
            if (outputStream == null) {
                return;
            }
            byte componentType = componentTypes[random.nextInt(componentTypes.length)];
            pendingFaultComponentType = componentType;
            for (int i = 0; i < 6; i++) {
                pendingFaultAddress[i] = (byte) random.nextInt(256);
            }

            byte[] data = buildComponentPayload(componentType, FireProtocolConstant.ComponentStatus.FAULT, pendingFaultAddress);
            byte[] message = protocolParser.buildMessage(
                    deviceType, deviceAddress, serverType, serverAddress,
                    serialNumber++, FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS, data);

            outputStream.write(message);
            outputStream.flush();
            sendSystemStatus(FireProtocolConstant.SystemStatus.FAULT);
            log.info("发送故障事件: 部件类型={}", toUnsigned(componentType));
        } catch (Exception e) {
            log.error("发送故障事件异常", e);
        }
    }

    private void sendFaultRestore() {
        if (!running.get()) {
            return;
        }
        try {
            if (outputStream == null) {
                return;
            }
            byte[] data = buildComponentPayload(pendingFaultComponentType, FireProtocolConstant.ComponentStatus.NORMAL, pendingFaultAddress);
            byte[] message = protocolParser.buildMessage(
                    deviceType, deviceAddress, serverType, serverAddress,
                    serialNumber++, FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS, data);

            outputStream.write(message);
            outputStream.flush();
            sendSystemStatus(FireProtocolConstant.SystemStatus.NORMAL);
            log.info("发送故障恢复事件: 部件类型={}", toUnsigned(pendingFaultComponentType));
        } catch (Exception e) {
            log.error("发送故障恢复事件异常", e);
        }
    }

    private static byte[] buildComponentPayload(byte componentType, byte componentStatus, byte[] addr6) {
        byte[] data = new byte[14];
        data[0] = componentType;
        data[1] = componentStatus;
        System.arraycopy(addr6, 0, data, 2, 6);
        LocalDateTime now = LocalDateTime.now();
        data[8] = (byte) (now.getYear() - 2000);
        data[9] = (byte) now.getMonthValue();
        data[10] = (byte) now.getDayOfMonth();
        data[11] = (byte) now.getHour();
        data[12] = (byte) now.getMinute();
        data[13] = (byte) now.getSecond();
        return data;
    }

    private static int toUnsigned(byte b) {
        return b & 0xFF;
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            log.error("关闭连接异常", e);
        }
        socket = null;
        outputStream = null;
        inputStream = null;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
