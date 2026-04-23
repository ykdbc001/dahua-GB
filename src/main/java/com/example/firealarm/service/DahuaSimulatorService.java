package com.example.firealarm.service;

import com.example.firealarm.constant.FireProtocolConstant;
import com.example.firealarm.protocol.dahua.DahuaProtocolParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 大华定制独立模拟器。
 *
 * <p>支持：
 * 1. 独立 TCP 连接与注册帧发送；
 * 2. IMEI / SN / ICCID / IMSI 等标识可配置；
 * 3. 单个设备状态、全部设备、全部状态矩阵触发；
 * 4. 与 demo 模拟器并行存在，互不影响。</p>
 */
@Service
public class DahuaSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(DahuaSimulatorService.class);

    private static final byte COMMAND_REGISTER = 0x00;
    private static final byte[] ALL_COMPONENT_TYPES = {
            FireProtocolConstant.ComponentType.FIRE_DETECTOR,
            FireProtocolConstant.ComponentType.MANUAL_ALARM_BUTTON,
            FireProtocolConstant.ComponentType.HYDRANT_BUTTON,
            FireProtocolConstant.ComponentType.DETECTION_CIRCUIT,
            FireProtocolConstant.ComponentType.FIRE_DISPLAY_PANEL,
            FireProtocolConstant.ComponentType.HEAT_DETECTOR,
            FireProtocolConstant.ComponentType.POINT_HEAT_DETECTOR,
            FireProtocolConstant.ComponentType.SMOKE_DETECTOR,
            FireProtocolConstant.ComponentType.POINT_ION_SMOKE_DETECTOR,
            FireProtocolConstant.ComponentType.POINT_PHOTOELECTRIC_SMOKE_DETECTOR,
            FireProtocolConstant.ComponentType.LINEAR_BEAM_SMOKE_DETECTOR,
            FireProtocolConstant.ComponentType.ASPIRATING_SMOKE_DETECTOR,
            FireProtocolConstant.ComponentType.COMPOSITE_FIRE_DETECTOR,
            FireProtocolConstant.ComponentType.COMPOSITE_SMOKE_HEAT_DETECTOR,
            FireProtocolConstant.ComponentType.COMPOSITE_LIGHT_HEAT_DETECTOR,
            FireProtocolConstant.ComponentType.COMPOSITE_LIGHT_SMOKE_DETECTOR,
            FireProtocolConstant.ComponentType.UV_FLAME_DETECTOR,
            FireProtocolConstant.ComponentType.IR_FLAME_DETECTOR,
            FireProtocolConstant.ComponentType.LIGHT_FIRE_DETECTOR,
            FireProtocolConstant.ComponentType.GAS_DETECTOR,
            FireProtocolConstant.ComponentType.IMAGE_FIRE_DETECTOR,
            FireProtocolConstant.ComponentType.SOUND_FIRE_DETECTOR,
            FireProtocolConstant.ComponentType.GAS_EXTINGUISHING_CONTROLLER,
            FireProtocolConstant.ComponentType.ELECTRICAL_FIRE_CONTROL,
            FireProtocolConstant.ComponentType.FIRE_CONTROL_DISPLAY,
            FireProtocolConstant.ComponentType.MODULE,
            FireProtocolConstant.ComponentType.INPUT_MODULE,
            FireProtocolConstant.ComponentType.OUTPUT_MODULE,
            FireProtocolConstant.ComponentType.IO_MODULE,
            FireProtocolConstant.ComponentType.RELAY_MODULE,
            FireProtocolConstant.ComponentType.FIRE_PUMP,
            FireProtocolConstant.ComponentType.FIRE_WATER_TANK,
            FireProtocolConstant.ComponentType.SPRINKLER_PUMP,
            FireProtocolConstant.ComponentType.WATER_FLOW_INDICATOR,
            FireProtocolConstant.ComponentType.SIGNAL_VALVE,
            FireProtocolConstant.ComponentType.ALARM_VALVE,
            FireProtocolConstant.ComponentType.PRESSURE_SWITCH,
            FireProtocolConstant.ComponentType.VALVE_DRIVER,
            FireProtocolConstant.ComponentType.FIRE_DOOR,
            FireProtocolConstant.ComponentType.FIRE_VALVE,
            FireProtocolConstant.ComponentType.VENTILATION_AC,
            FireProtocolConstant.ComponentType.FOAM_PUMP,
            FireProtocolConstant.ComponentType.PIPE_SOLENOID_VALVE,
            FireProtocolConstant.ComponentType.SMOKE_EXHAUST_FAN,
            FireProtocolConstant.ComponentType.SMOKE_EXHAUST_FIRE_VALVE,
            FireProtocolConstant.ComponentType.NORMALLY_CLOSED_AIR_INLET,
            FireProtocolConstant.ComponentType.SMOKE_EXHAUST_OUTLET,
            FireProtocolConstant.ComponentType.ELECTRIC_SMOKE_BARRIER,
            FireProtocolConstant.ComponentType.FIRE_CURTAIN_CONTROLLER,
            FireProtocolConstant.ComponentType.FIRE_DOOR_MONITOR,
            FireProtocolConstant.ComponentType.ALARM_DEVICE
    };
    private static final byte[] ALL_COMPONENT_STATUSES = {
            FireProtocolConstant.ComponentStatus.NORMAL,
            FireProtocolConstant.ComponentStatus.FIRE_ALARM,
            FireProtocolConstant.ComponentStatus.FAULT,
            FireProtocolConstant.ComponentStatus.FEEDBACK,
            FireProtocolConstant.ComponentStatus.SHIELD,
            FireProtocolConstant.ComponentStatus.SUPERVISION,
            FireProtocolConstant.ComponentStatus.START,
            FireProtocolConstant.ComponentStatus.STOP,
            FireProtocolConstant.ComponentStatus.TEST
    };

    @Value("${fire-alarm.server.port:9000}")
    private int serverPort;

    private final DahuaProtocolParser protocolParser;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();

    private volatile ScheduledExecutorService scheduler;
    private volatile Thread receiveThread;
    private volatile Socket socket;
    private volatile OutputStream outputStream;
    private volatile InputStream inputStream;
    private volatile int serialNumber = 1;

    private volatile String serverHost = "localhost";
    private volatile byte deviceType = FireProtocolConstant.ControlUnitType.FIRE_ALARM_SYSTEM;
    private volatile byte serverType = 0x00;
    private volatile String serverAddress = "FFFFFFFFFFFF";
    private volatile String imei = "865176080842620";
    private volatile String imsi = "460001234567890";
    private volatile String iccid = "89860012345678901234";
    private volatile String sn = "DH-SIM-00000001";
    private volatile String productModel = "DH-HY-SIM";
    private volatile int keepaliveSeconds = 28800;
    private volatile boolean autoHeartbeat = true;

    public DahuaSimulatorService(DahuaProtocolParser protocolParser) {
        this.protocolParser = protocolParser;
    }

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
        scheduledTasks.add(scheduler.schedule(this::startInternal, 300, TimeUnit.MILLISECONDS));
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        for (ScheduledFuture<?> task : scheduledTasks) {
            task.cancel(false);
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
        log.info("[大华模拟器] 已停止");
    }

    public synchronized Map<String, Object> updateConfig(Map<String, Object> body) {
        if (body == null) {
            return getStatus();
        }
        if (body.containsKey("serverHost")) {
            serverHost = String.valueOf(body.get("serverHost"));
        }
        if (body.containsKey("serverAddress")) {
            serverAddress = String.valueOf(body.get("serverAddress"));
        }
        if (body.containsKey("deviceType")) {
            deviceType = parseByte(body.get("deviceType"), deviceType);
        }
        if (body.containsKey("serverType")) {
            serverType = parseByte(body.get("serverType"), serverType);
        }
        if (body.containsKey("imei")) {
            imei = digitsOnly(String.valueOf(body.get("imei")), imei);
        }
        if (body.containsKey("imsi")) {
            imsi = digitsOnly(String.valueOf(body.get("imsi")), imsi);
        }
        if (body.containsKey("iccid")) {
            iccid = digitsOnly(String.valueOf(body.get("iccid")), iccid);
        }
        if (body.containsKey("sn")) {
            sn = String.valueOf(body.get("sn"));
        }
        if (body.containsKey("productModel")) {
            productModel = String.valueOf(body.get("productModel"));
        }
        if (body.containsKey("keepaliveSeconds")) {
            keepaliveSeconds = Math.max(1, parseInt(body.get("keepaliveSeconds"), keepaliveSeconds));
        }
        if (body.containsKey("autoHeartbeat")) {
            autoHeartbeat = Boolean.parseBoolean(String.valueOf(body.get("autoHeartbeat")));
        }
        return getStatus();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running.get());
        status.put("serverHost", serverHost);
        status.put("serverPort", serverPort);
        status.put("deviceType", deviceType & 0xFF);
        status.put("serverType", serverType & 0xFF);
        status.put("serverAddress", serverAddress);
        status.put("imei", imei);
        status.put("sourceAddress", imei.length() > 12 ? imei.substring(imei.length() - 12) : imei);
        status.put("imsi", imsi);
        status.put("iccid", iccid);
        status.put("sn", sn);
        status.put("productModel", productModel);
        status.put("keepaliveSeconds", keepaliveSeconds);
        status.put("autoHeartbeat", autoHeartbeat);
        return status;
    }

    public List<Map<String, Object>> getCatalog() {
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (byte type : ALL_COMPONENT_TYPES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("componentType", type & 0xFF);
            List<Integer> supportedStatuses = new ArrayList<Integer>();
            for (byte status : ALL_COMPONENT_STATUSES) {
                supportedStatuses.add(status & 0xFF);
            }
            item.put("supportedStatuses", supportedStatuses);
            catalog.add(item);
        }
        return catalog;
    }

    public synchronized void sendRegistration() {
        ensureConnected();
        sendRaw(buildRegistrationFrame(), "[大华模拟器] 发送注册帧");
    }

    public synchronized void sendSystemStatus(byte status) {
        ensureConnected();
        sendRaw(buildSystemStatusFrame(status), "[大华模拟器] 发送系统状态 " + (status & 0xFF));
    }

    public synchronized void sendComponentStatus(byte componentType, byte componentStatus, String componentAddress) {
        ensureConnected();
        byte[] payload = buildComponentPayload(componentType, componentStatus, addressStringToBytes(componentAddress));
        byte[] frame = protocolParser.buildMessage(deviceType, imei, serverType, serverAddress,
                serialNumber++, FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS, payload);
        sendRaw(frame, "[大华模拟器] 发送部件状态 type=" + (componentType & 0xFF) + " status=" + (componentStatus & 0xFF));
    }

    public synchronized void sendAllDevices(byte componentStatus) {
        ensureConnected();
        for (int i = 0; i < ALL_COMPONENT_TYPES.length; i++) {
            sendComponentStatus(ALL_COMPONENT_TYPES[i], componentStatus, generateAddressHex(i));
        }
    }

    public synchronized void sendAllStates(byte componentType) {
        ensureConnected();
        for (int i = 0; i < ALL_COMPONENT_STATUSES.length; i++) {
            sendComponentStatus(componentType, ALL_COMPONENT_STATUSES[i], generateAddressHex(i));
        }
    }

    public synchronized void sendFullMatrix() {
        ensureConnected();
        int idx = 0;
        for (byte componentType : ALL_COMPONENT_TYPES) {
            for (byte status : ALL_COMPONENT_STATUSES) {
                sendComponentStatus(componentType, status, generateAddressHex(idx++));
            }
        }
    }

    private void startInternal() {
        if (!running.get()) {
            return;
        }
        try {
            socket = new Socket(serverHost, serverPort);
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            log.info("[大华模拟器] 已连接到 {}:{}", serverHost, serverPort);

            receiveThread = new Thread(this::receiveMessages, "dahua-simulator-recv");
            receiveThread.setDaemon(true);
            receiveThread.start();

            sendRegistration();
            sendSystemStatus(FireProtocolConstant.SystemStatus.NORMAL);

            if (autoHeartbeat) {
                scheduledTasks.add(scheduler.scheduleAtFixedRate(() -> {
                    if (running.get()) {
                        sendSystemStatus(FireProtocolConstant.SystemStatus.NORMAL);
                    }
                }, 30, 30, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            log.error("[大华模拟器] 启动异常", e);
            running.set(false);
            closeConnection();
        }
    }

    private void receiveMessages() {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while (running.get() && (bytesRead = inputStream.read(buffer)) != -1) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                log.info("[大华模拟器] 收到服务端响应: {}", bytesToHex(data));
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("[大华模拟器] 接收消息异常", e);
            }
        }
    }

    private void ensureConnected() {
        if (outputStream == null) {
            throw new IllegalStateException("大华模拟器未连接，请先启动");
        }
    }

    private byte[] buildRegistrationFrame() {
        byte[] payload = buildRegistrationPayload();
        return protocolParser.buildMessage(deviceType, imei, serverType, serverAddress,
                serialNumber++, COMMAND_REGISTER, payload);
    }

    private byte[] buildSystemStatusFrame(byte status) {
        byte[] payload = new byte[13];
        payload[0] = status;
        byte[] address = addressStringToBytes(sourceAddress12());
        System.arraycopy(address, 0, payload, 1, 6);
        fillOccurTime(payload, 7);
        return protocolParser.buildMessage(deviceType, imei, serverType, serverAddress,
                serialNumber++, FireProtocolConstant.CommandType.UPLOAD_SYSTEM_STATUS, payload);
    }

    private byte[] buildRegistrationPayload() {
        byte[] payload = new byte[66];
        int offset = 0;

        payload[offset++] = 0x01;
        payload[offset++] = 0x04;
        payload[offset++] = 0x01;
        payload[offset++] = 0x00;

        putAsciiRightPadded(payload, offset, 16, sn);
        offset += 16;
        putBcdLeftPadded(payload, offset, 8, imei, 16);
        offset += 8;
        putBcdLeftPadded(payload, offset, 8, imsi, 16);
        offset += 8;
        putBcdLeftPadded(payload, offset, 10, iccid, 20);
        offset += 10;
        putUIntLe(payload, offset, 4, keepaliveSeconds);
        offset += 4;
        putAsciiRightPadded(payload, offset, 16, productModel);
        return payload;
    }

    private static byte[] buildComponentPayload(byte componentType, byte componentStatus, byte[] addr6) {
        byte[] data = new byte[14];
        data[0] = componentType;
        data[1] = componentStatus;
        System.arraycopy(addr6, 0, data, 2, 6);
        fillOccurTime(data, 8);
        return data;
    }

    private static void fillOccurTime(byte[] buffer, int offset) {
        LocalDateTime now = LocalDateTime.now();
        buffer[offset] = (byte) (now.getYear() - 2000);
        buffer[offset + 1] = (byte) now.getMonthValue();
        buffer[offset + 2] = (byte) now.getDayOfMonth();
        buffer[offset + 3] = (byte) now.getHour();
        buffer[offset + 4] = (byte) now.getMinute();
        buffer[offset + 5] = (byte) now.getSecond();
    }

    private void sendRaw(byte[] frame, String action) {
        try {
            outputStream.write(frame);
            outputStream.flush();
            log.info("{}: {}", action, bytesToHex(frame));
        } catch (Exception e) {
            throw new IllegalStateException("发送大华模拟报文失败", e);
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            log.error("[大华模拟器] 关闭连接异常", e);
        }
        socket = null;
        outputStream = null;
        inputStream = null;
    }

    private String sourceAddress12() {
        return imei.length() > 12 ? imei.substring(imei.length() - 12) : imei;
    }

    private byte[] addressStringToBytes(String hexOrDigits) {
        String normalized = hexOrDigits == null ? "" : hexOrDigits.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        if (normalized.isEmpty()) {
            normalized = "000000000000";
        }
        if (normalized.length() > 12) {
            normalized = normalized.substring(normalized.length() - 12);
        }
        while (normalized.length() < 12) {
            normalized = "0" + normalized;
        }
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private String generateAddressHex(int seed) {
        return String.format("%012X", 0x100000 + (seed & 0xFFFFFF));
    }

    private static void putAsciiRightPadded(byte[] target, int offset, int len, String value) {
        byte[] src = value == null ? new byte[0] : value.getBytes(StandardCharsets.US_ASCII);
        int copyLen = Math.min(len, src.length);
        System.arraycopy(src, 0, target, offset, copyLen);
        for (int i = copyLen; i < len; i++) {
            target[offset + i] = 0x00;
        }
    }

    private static void putBcdLeftPadded(byte[] target, int offset, int bytesLen, String digits, int totalDigits) {
        String normalized = digits == null ? "" : digits.replaceAll("\\D", "");
        if (normalized.length() > totalDigits) {
            normalized = normalized.substring(normalized.length() - totalDigits);
        }
        while (normalized.length() < totalDigits) {
            normalized = "0" + normalized;
        }
        for (int i = 0; i < bytesLen; i++) {
            int hi = normalized.charAt(i * 2) - '0';
            int lo = normalized.charAt(i * 2 + 1) - '0';
            target[offset + i] = (byte) ((hi << 4) | lo);
        }
    }

    private static void putUIntLe(byte[] target, int offset, int bytesLen, long value) {
        for (int i = 0; i < bytesLen; i++) {
            target[offset + i] = (byte) ((value >> (i * 8)) & 0xFF);
        }
    }

    private static String digitsOnly(String raw, String fallback) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        return digits.isEmpty() ? fallback : digits;
    }

    private static byte parseByte(Object raw, byte fallback) {
        return (byte) parseInt(raw, fallback & 0xFF);
    }

    private static int parseInt(Object raw, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
