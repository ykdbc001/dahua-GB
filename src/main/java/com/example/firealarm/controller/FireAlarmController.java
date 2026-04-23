package com.example.firealarm.controller;

import com.example.firealarm.dto.SystemStatusDto;
import com.example.firealarm.model.FireAlarmEvent;
import com.example.firealarm.protocol.ProtocolMode;
import com.example.firealarm.service.DahuaFrameHistoryService;
import com.example.firealarm.service.DahuaSimulatorService;
import com.example.firealarm.service.FireAlarmNotificationService;
import com.example.firealarm.service.FireAlarmSimulatorService;
import com.example.firealarm.service.ProtocolModeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消防报警控制器
 * 提供查询报警事件和设备状态的REST API
 */
@RestController
@RequestMapping("/api/fire-alarm")
public class FireAlarmController {

    @Autowired
    private FireAlarmNotificationService notificationService;

    @Autowired
    private FireAlarmSimulatorService simulatorService;

    @Autowired
    private ProtocolModeService protocolModeService;

    @Autowired
    private DahuaSimulatorService dahuaSimulatorService;

    @Autowired
    private DahuaFrameHistoryService dahuaFrameHistoryService;

    /**
     * 获取当前活跃的报警事件
     *
     * @return 报警事件列表
     */
    @GetMapping("/active-alarms")
    public ResponseEntity<List<FireAlarmEvent>> getActiveAlarms() {
        List<FireAlarmEvent> alarms = notificationService.getActiveAlarms();
        return ResponseEntity.ok(alarms);
    }

    /**
     * 获取系统状态信息
     *
     * @return 系统状态
     */
    @GetMapping("/system-status")
    public ResponseEntity<SystemStatusDto> getSystemStatus() {
        return ResponseEntity.ok(notificationService.getSystemStatus());
    }

    @GetMapping("/simulator/status")
    public ResponseEntity<Map<String, Object>> getSimulatorStatus() {
        Map<String, Object> body = new HashMap<String, Object>();
        ProtocolMode mode = protocolModeService.getMode();
        boolean dahua = mode == ProtocolMode.DAHUA;
        body.put("running", dahua ? dahuaSimulatorService.isRunning() : simulatorService.isRunning());
        body.put("mode", mode.name().toLowerCase());
        body.put("simulatorType", dahua ? "dahua" : "demo");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/simulator/start")
    public ResponseEntity<Map<String, Object>> startSimulator() {
        Map<String, Object> body = new HashMap<String, Object>();
        ProtocolMode mode = protocolModeService.getMode();
        if (mode == ProtocolMode.DAHUA) {
            dahuaSimulatorService.start();
            body.put("running", dahuaSimulatorService.isRunning());
            body.put("simulatorType", "dahua");
        } else {
            simulatorService.start();
            body.put("running", simulatorService.isRunning());
            body.put("simulatorType", "demo");
        }
        body.put("mode", mode.name().toLowerCase());
        body.put("ok", true);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/simulator/stop")
    public ResponseEntity<Map<String, Object>> stopSimulator() {
        Map<String, Object> body = new HashMap<String, Object>();
        ProtocolMode mode = protocolModeService.getMode();
        if (mode == ProtocolMode.DAHUA) {
            dahuaSimulatorService.stop();
            body.put("running", dahuaSimulatorService.isRunning());
            body.put("simulatorType", "dahua");
        } else {
            simulatorService.stop();
            body.put("running", simulatorService.isRunning());
            body.put("simulatorType", "demo");
        }
        body.put("mode", mode.name().toLowerCase());
        body.put("ok", true);
        return ResponseEntity.ok(body);
    }

    /**
     * TCP 9000 解析模式：demo=原国标 demo；dahua=大华包内独立解析/应答（可演进）
     */
    @GetMapping("/protocol-mode")
    public ResponseEntity<Map<String, String>> getProtocolMode() {
        Map<String, String> body = new HashMap<>();
        body.put("mode", protocolModeService.getMode().name().toLowerCase());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/protocol-mode")
    public ResponseEntity<Map<String, Object>> setProtocolMode(@RequestBody Map<String, String> body) {
        String raw = body != null ? body.get("mode") : null;
        protocolModeService.setModeFromString(raw);
        ProtocolMode mode = protocolModeService.getMode();
        if (mode == ProtocolMode.DAHUA) {
            simulatorService.stop();
        } else {
            dahuaSimulatorService.stop();
        }
        Map<String, Object> res = new HashMap<String, Object>();
        res.put("ok", true);
        res.put("mode", mode.name().toLowerCase());
        res.put("simulatorType", mode == ProtocolMode.DAHUA ? "dahua" : "demo");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/dahua-simulator/status")
    public ResponseEntity<Map<String, Object>> getDahuaSimulatorStatus() {
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @GetMapping("/dahua-simulator/catalog")
    public ResponseEntity<List<Map<String, Object>>> getDahuaSimulatorCatalog() {
        return ResponseEntity.ok(dahuaSimulatorService.getCatalog());
    }

    @PostMapping("/dahua-simulator/config")
    public ResponseEntity<Map<String, Object>> setDahuaSimulatorConfig(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(dahuaSimulatorService.updateConfig(body));
    }

    @PostMapping("/dahua-simulator/start")
    public ResponseEntity<Map<String, Object>> startDahuaSimulator() {
        dahuaSimulatorService.start();
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/stop")
    public ResponseEntity<Map<String, Object>> stopDahuaSimulator() {
        dahuaSimulatorService.stop();
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/register")
    public ResponseEntity<Map<String, Object>> sendDahuaRegister() {
        dahuaSimulatorService.sendRegistration();
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/system-status")
    public ResponseEntity<Map<String, Object>> sendDahuaSystemStatus(@RequestBody Map<String, Object> body) {
        byte status = (byte) parseInt(body, "status", 0);
        dahuaSimulatorService.sendSystemStatus(status);
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/component-status")
    public ResponseEntity<Map<String, Object>> sendDahuaComponentStatus(@RequestBody Map<String, Object> body) {
        byte componentType = (byte) parseInt(body, "componentType", 0x19);
        byte componentStatus = (byte) parseInt(body, "componentStatus", 0x01);
        String componentAddress = String.valueOf(body.getOrDefault("componentAddress", "000000000001"));
        dahuaSimulatorService.sendComponentStatus(componentType, componentStatus, componentAddress);
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/all-devices")
    public ResponseEntity<Map<String, Object>> sendDahuaAllDevices(@RequestBody Map<String, Object> body) {
        byte componentStatus = (byte) parseInt(body, "componentStatus", 0x01);
        dahuaSimulatorService.sendAllDevices(componentStatus);
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/bound-status")
    public ResponseEntity<Map<String, Object>> sendDahuaBoundStatus(@RequestBody Map<String, Object> body) {
        byte componentStatus = (byte) parseInt(body, "componentStatus", 0x01);
        dahuaSimulatorService.sendBoundStatus(componentStatus);
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/generate-all-terminals")
    public ResponseEntity<Map<String, Object>> generateAllDahuaTerminals() {
        return ResponseEntity.ok(dahuaSimulatorService.generateAllTerminals());
    }

    @PostMapping("/dahua-simulator/generate-all-and-simulate")
    public ResponseEntity<Map<String, Object>> generateAllDahuaAndSimulate() {
        return ResponseEntity.ok(dahuaSimulatorService.generateAllAndSimulate());
    }

    @PostMapping("/dahua-simulator/all-states")
    public ResponseEntity<Map<String, Object>> sendDahuaAllStates(@RequestBody Map<String, Object> body) {
        byte componentType = (byte) parseInt(body, "componentType", 0x19);
        dahuaSimulatorService.sendAllStates(componentType);
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @PostMapping("/dahua-simulator/full-matrix")
    public ResponseEntity<Map<String, Object>> sendDahuaFullMatrix() {
        dahuaSimulatorService.sendFullMatrix();
        return ResponseEntity.ok(dahuaSimulatorService.getStatus());
    }

    @GetMapping("/dahua/frames")
    public ResponseEntity<List<Map<String, Object>>> getDahuaFrames() {
        return ResponseEntity.ok(dahuaFrameHistoryService.list());
    }

    @PostMapping("/dahua/frames/clear")
    public ResponseEntity<Map<String, Object>> clearDahuaFrames() {
        dahuaFrameHistoryService.clear();
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("ok", true);
        return ResponseEntity.ok(body);
    }

    private static int parseInt(Map<String, Object> body, String key, int defaultValue) {
        if (body == null || !body.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(body.get(key)));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
