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
 * <p>支持单终端和终端池两种模式：
 * 1. 默认仍可按原有接口模拟单个 IMEI；
 * 2. 可通过 terminals 配置多个终端，每个 IMEI 绑定一个设备类型与地址；
 * 3. start/register/system-status 会作用到全部终端；
 * 4. bound-status 会按终端各自绑定的设备类型/地址群发指定状态。</p>
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
    private final CopyOnWriteArrayList<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<ScheduledFuture<?>>();

    private volatile ScheduledExecutorService scheduler;
    private volatile String serverHost = "localhost";
    private volatile byte serverType = 0x00;
    private volatile String serverAddress = "FFFFFFFFFFFF";
    private volatile byte deviceType = FireProtocolConstant.ControlUnitType.FIRE_ALARM_SYSTEM;
    private volatile boolean autoHeartbeat = true;
    private volatile int keepaliveSeconds = 28800;

    private final List<TerminalConfig> terminals = new ArrayList<TerminalConfig>();
    private final List<TerminalSession> sessions = new ArrayList<TerminalSession>();

    public DahuaSimulatorService(DahuaProtocolParser protocolParser) {
        this.protocolParser = protocolParser;
        this.terminals.add(defaultTerminal());
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        scheduler = Executors.newScheduledThreadPool(Math.max(2, terminals.size() * 2));
        scheduledTasks.clear();
        scheduledTasks.add(scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                startInternal();
            }
        }, 300, TimeUnit.MILLISECONDS));
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
        closeAllSessions();
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
        if (body.containsKey("serverType")) {
            serverType = parseByte(body.get("serverType"), serverType);
        }
        if (body.containsKey("deviceType")) {
            deviceType = parseByte(body.get("deviceType"), deviceType);
        }
        if (body.containsKey("keepaliveSeconds")) {
            keepaliveSeconds = Math.max(1, parseInt(body.get("keepaliveSeconds"), keepaliveSeconds));
        }
        if (body.containsKey("autoHeartbeat")) {
            autoHeartbeat = Boolean.parseBoolean(String.valueOf(body.get("autoHeartbeat")));
        }

        if (body.containsKey("terminals") && body.get("terminals") instanceof List) {
            replaceTerminals((List<?>) body.get("terminals"));
        } else {
            TerminalConfig terminal = ensurePrimaryTerminal();
            if (body.containsKey("imei")) {
                terminal.imei = digitsOnly(String.valueOf(body.get("imei")), terminal.imei);
            }
            if (body.containsKey("imsi")) {
                terminal.imsi = digitsOnly(String.valueOf(body.get("imsi")), terminal.imsi);
            }
            if (body.containsKey("iccid")) {
                terminal.iccid = digitsOnly(String.valueOf(body.get("iccid")), terminal.iccid);
            }
            if (body.containsKey("sn")) {
                terminal.sn = String.valueOf(body.get("sn"));
            }
            if (body.containsKey("productModel")) {
                terminal.productModel = String.valueOf(body.get("productModel"));
            }
            if (body.containsKey("componentType")) {
                terminal.componentType = parseByte(body.get("componentType"), terminal.componentType);
            }
            if (body.containsKey("componentAddress")) {
                terminal.componentAddress = normalizeHexAddress(String.valueOf(body.get("componentAddress")));
            }
        }

        if (running.get()) {
            stop();
        }
        return getStatus();
    }

    public synchronized Map<String, Object> getStatus() {
        LinkedHashMap<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("running", running.get());
        status.put("serverHost", serverHost);
        status.put("serverPort", serverPort);
        status.put("deviceType", deviceType & 0xFF);
        status.put("serverType", serverType & 0xFF);
        status.put("serverAddress", serverAddress);
        status.put("autoHeartbeat", autoHeartbeat);
        status.put("keepaliveSeconds", keepaliveSeconds);
        status.put("terminalCount", terminals.size());
        status.put("terminals", summarizeTerminals());

        TerminalConfig primary = ensurePrimaryTerminal();
        status.put("imei", primary.imei);
        status.put("sourceAddress", sourceAddress12(primary.imei));
        status.put("imsi", primary.imsi);
        status.put("iccid", primary.iccid);
        status.put("sn", primary.sn);
        status.put("productModel", primary.productModel);
        status.put("componentType", primary.componentType & 0xFF);
        status.put("componentAddress", primary.componentAddress);
        return status;
    }

    public List<Map<String, Object>> getCatalog() {
        List<Map<String, Object>> catalog = new ArrayList<Map<String, Object>>();
        for (byte type : ALL_COMPONENT_TYPES) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
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
        for (TerminalSession session : sessions) {
            sendRegistration(session);
        }
    }

    public synchronized void sendSystemStatus(byte status) {
        ensureConnected();
        for (TerminalSession session : sessions) {
            sendSystemStatus(session, status);
        }
    }

    public synchronized void sendComponentStatus(byte componentType, byte componentStatus, String componentAddress) {
        ensureConnected();
        String normalizedAddress = normalizeHexAddress(componentAddress);
        for (TerminalSession session : sessions) {
            sendComponentStatus(session, componentType, componentStatus, normalizedAddress);
        }
    }

    public synchronized void sendAllDevices(byte componentStatus) {
        ensureConnected();
        for (TerminalSession session : sessions) {
            sendComponentStatus(session, session.config.componentType, componentStatus, session.config.componentAddress);
        }
    }

    public synchronized void sendAllStates(byte componentType) {
        ensureConnected();
        for (TerminalSession session : sessions) {
            for (byte status : ALL_COMPONENT_STATUSES) {
                sendComponentStatus(session, componentType, status, session.config.componentAddress);
            }
        }
    }

    public synchronized void sendFullMatrix() {
        ensureConnected();
        int idx = 0;
        for (TerminalSession session : sessions) {
            for (byte status : ALL_COMPONENT_STATUSES) {
                sendComponentStatus(session, session.config.componentType, status, generateAddressHex(idx++));
            }
        }
    }

    public synchronized void sendBoundStatus(byte componentStatus) {
        ensureConnected();
        for (TerminalSession session : sessions) {
            sendComponentStatus(session, session.config.componentType, componentStatus, session.config.componentAddress);
        }
    }

    private void startInternal() {
        if (!running.get()) {
            return;
        }
        try {
            sessions.clear();
            for (TerminalConfig terminal : terminals) {
                TerminalSession session = openSession(terminal);
                sessions.add(session);
                sendRegistration(session);
                sendSystemStatus(session, FireProtocolConstant.SystemStatus.NORMAL);
            }

            if (autoHeartbeat) {
                scheduledTasks.add(scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        if (running.get()) {
                            sendSystemStatus(FireProtocolConstant.SystemStatus.NORMAL);
                        }
                    }
                }, 30, 30, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            log.error("[大华模拟器] 启动异常", e);
            running.set(false);
            closeAllSessions();
        }
    }

    private TerminalSession openSession(final TerminalConfig terminal) throws Exception {
        TerminalSession session = new TerminalSession();
        session.config = terminal.copy();
        session.socket = new Socket(serverHost, serverPort);
        session.outputStream = session.socket.getOutputStream();
        session.inputStream = session.socket.getInputStream();
        log.info("[大华模拟器] 终端 {} 已连接到 {}:{}", terminal.imei, serverHost, serverPort);

        session.receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveMessages(terminal.imei, session);
            }
        }, "dahua-simulator-recv-" + sourceAddress12(terminal.imei));
        session.receiveThread.setDaemon(true);
        session.receiveThread.start();
        return session;
    }

    private void receiveMessages(String imeiValue, TerminalSession session) {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while (running.get() && session.inputStream != null && (bytesRead = session.inputStream.read(buffer)) != -1) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                log.info("[大华模拟器][{}] 收到服务端响应: {}", imeiValue, bytesToHex(data));
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("[大华模拟器][{}] 接收消息异常", imeiValue, e);
            }
        }
    }

    private void ensureConnected() {
        if (sessions.isEmpty()) {
            throw new IllegalStateException("大华模拟器未连接，请先启动");
        }
    }

    private void sendRegistration(TerminalSession session) {
        byte[] payload = buildRegistrationPayload(session.config);
        byte[] frame = protocolParser.buildMessage(deviceType, session.config.imei, serverType, serverAddress,
                session.serialNumber++, COMMAND_REGISTER, payload);
        sendRaw(session, frame, "[大华模拟器][" + session.config.imei + "] 发送注册帧");
    }

    private void sendSystemStatus(TerminalSession session, byte status) {
        byte[] payload = new byte[13];
        payload[0] = status;
        byte[] address = addressStringToBytes(sourceAddress12(session.config.imei));
        System.arraycopy(address, 0, payload, 1, 6);
        fillOccurTime(payload, 7);
        byte[] frame = protocolParser.buildMessage(deviceType, session.config.imei, serverType, serverAddress,
                session.serialNumber++, FireProtocolConstant.CommandType.UPLOAD_SYSTEM_STATUS, payload);
        sendRaw(session, frame, "[大华模拟器][" + session.config.imei + "] 发送系统状态 " + (status & 0xFF));
    }

    private void sendComponentStatus(TerminalSession session, byte componentType, byte componentStatus, String componentAddress) {
        byte[] payload = buildComponentPayload(componentType, componentStatus, addressStringToBytes(componentAddress));
        byte[] frame = protocolParser.buildMessage(deviceType, session.config.imei, serverType, serverAddress,
                session.serialNumber++, FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS, payload);
        sendRaw(session, frame, "[大华模拟器][" + session.config.imei + "] 发送部件状态 type="
                + (componentType & 0xFF) + " status=" + (componentStatus & 0xFF) + " addr=" + componentAddress);
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

    private byte[] buildRegistrationPayload(TerminalConfig terminal) {
        byte[] payload = new byte[66];
        int offset = 0;
        payload[offset++] = 0x01;
        payload[offset++] = 0x04;
        payload[offset++] = 0x01;
        payload[offset++] = 0x00;

        putAsciiRightPadded(payload, offset, 16, terminal.sn);
        offset += 16;
        putBcdLeftPadded(payload, offset, 8, terminal.imei, 16);
        offset += 8;
        putBcdLeftPadded(payload, offset, 8, terminal.imsi, 16);
        offset += 8;
        putBcdLeftPadded(payload, offset, 10, terminal.iccid, 20);
        offset += 10;
        putUIntLe(payload, offset, 4, keepaliveSeconds);
        offset += 4;
        putAsciiRightPadded(payload, offset, 16, terminal.productModel);
        return payload;
    }

    private void sendRaw(TerminalSession session, byte[] frame, String action) {
        try {
            session.outputStream.write(frame);
            session.outputStream.flush();
            log.info("{}: {}", action, bytesToHex(frame));
        } catch (Exception e) {
            throw new IllegalStateException("发送大华模拟报文失败", e);
        }
    }

    private void replaceTerminals(List<?> rawTerminals) {
        terminals.clear();
        for (Object item : rawTerminals) {
            if (!(item instanceof Map)) {
                continue;
            }
            TerminalConfig terminal = defaultTerminal();
            Map<?, ?> raw = (Map<?, ?>) item;
            if (raw.containsKey("imei")) {
                terminal.imei = digitsOnly(String.valueOf(raw.get("imei")), terminal.imei);
            }
            if (raw.containsKey("imsi")) {
                terminal.imsi = digitsOnly(String.valueOf(raw.get("imsi")), terminal.imsi);
            }
            if (raw.containsKey("iccid")) {
                terminal.iccid = digitsOnly(String.valueOf(raw.get("iccid")), terminal.iccid);
            }
            if (raw.containsKey("sn")) {
                terminal.sn = String.valueOf(raw.get("sn"));
            }
            if (raw.containsKey("productModel")) {
                terminal.productModel = String.valueOf(raw.get("productModel"));
            }
            if (raw.containsKey("componentType")) {
                terminal.componentType = parseByte(raw.get("componentType"), terminal.componentType);
            }
            if (raw.containsKey("componentAddress")) {
                terminal.componentAddress = normalizeHexAddress(String.valueOf(raw.get("componentAddress")));
            }
            terminals.add(terminal);
        }
        if (terminals.isEmpty()) {
            terminals.add(defaultTerminal());
        }
    }

    private TerminalConfig ensurePrimaryTerminal() {
        if (terminals.isEmpty()) {
            terminals.add(defaultTerminal());
        }
        return terminals.get(0);
    }

    private List<Map<String, Object>> summarizeTerminals() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (TerminalConfig terminal : terminals) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("imei", terminal.imei);
            item.put("sourceAddress", sourceAddress12(terminal.imei));
            item.put("imsi", terminal.imsi);
            item.put("iccid", terminal.iccid);
            item.put("sn", terminal.sn);
            item.put("productModel", terminal.productModel);
            item.put("componentType", terminal.componentType & 0xFF);
            item.put("componentAddress", terminal.componentAddress);
            list.add(item);
        }
        return list;
    }

    private void closeAllSessions() {
        for (TerminalSession session : sessions) {
            try {
                if (session.receiveThread != null) {
                    session.receiveThread.interrupt();
                }
                if (session.socket != null && !session.socket.isClosed()) {
                    session.socket.close();
                }
            } catch (Exception e) {
                log.error("[大华模拟器] 关闭终端 {} 连接异常", session.config != null ? session.config.imei : "unknown", e);
            }
        }
        sessions.clear();
    }

    private static TerminalConfig defaultTerminal() {
        TerminalConfig terminal = new TerminalConfig();
        terminal.imei = "865176080842620";
        terminal.imsi = "460001234567890";
        terminal.iccid = "89860012345678901234";
        terminal.sn = "DH-SIM-00000001";
        terminal.productModel = "DH-HY-SIM";
        terminal.componentType = FireProtocolConstant.ComponentType.GAS_DETECTOR;
        terminal.componentAddress = "000000000001";
        return terminal;
    }

    private static String sourceAddress12(String imeiValue) {
        return imeiValue.length() > 12 ? imeiValue.substring(imeiValue.length() - 12) : imeiValue;
    }

    private static byte[] addressStringToBytes(String hexOrDigits) {
        String normalized = normalizeHexAddress(hexOrDigits);
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String normalizeHexAddress(String hexOrDigits) {
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
        return normalized;
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

    private static class TerminalConfig {
        private String imei;
        private String imsi;
        private String iccid;
        private String sn;
        private String productModel;
        private byte componentType;
        private String componentAddress;

        private TerminalConfig copy() {
            TerminalConfig copy = new TerminalConfig();
            copy.imei = this.imei;
            copy.imsi = this.imsi;
            copy.iccid = this.iccid;
            copy.sn = this.sn;
            copy.productModel = this.productModel;
            copy.componentType = this.componentType;
            copy.componentAddress = this.componentAddress;
            return copy;
        }
    }

    private static class TerminalSession {
        private TerminalConfig config;
        private Socket socket;
        private OutputStream outputStream;
        private InputStream inputStream;
        private Thread receiveThread;
        private int serialNumber = 1;
    }
}
