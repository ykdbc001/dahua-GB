package com.example.firealarm.service;

import com.example.firealarm.constant.FireProtocolConstant;
import com.example.firealarm.model.FireAlarmEvent;
import com.example.firealarm.model.FireMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 消防报警事件处理器
 */
@Service
public class FireAlarmEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FireAlarmEventHandler.class);

    /**
     * 处理消防协议消息，转换为报警事件
     *
     * @param message 消防协议消息
     * @return 报警事件对象，如果不是报警事件则返回null
     */
    public FireAlarmEvent handleMessage(FireMessage message) {
        if (message == null) {
            return null;
        }

        try {
            // 只处理上传系统状态和上传部件运行状态的命令
            if (message.getCommand() != FireProtocolConstant.CommandType.UPLOAD_SYSTEM_STATUS &&
                message.getCommand() != FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS) {
                return null;
            }

            FireAlarmEvent event = new FireAlarmEvent();
            event.setId(UUID.randomUUID().toString());
            event.setDeviceAddress(message.getSourceAddress());
            event.setControllerType(message.getSourceType());
            event.setReceiveTime(LocalDateTime.now());
            event.setOriginalMessage(message);

            byte[] data = message.getData();
            if (data == null || data.length == 0) {
                log.error("消息数据内容为空");
                return null;
            }

            // 解析不同类型的命令
            switch (message.getCommand()) {
                case FireProtocolConstant.CommandType.UPLOAD_SYSTEM_STATUS:
                    parseSystemStatus(event, data);
                    break;
                case FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS:
                    parseComponentStatus(event, data);
                    break;
                default:
                    return null;
            }

            // 生成事件描述
            generateEventDescription(event);

            return event;
        } catch (Exception e) {
            log.error("处理消防消息异常", e);
            return null;
        }
    }

    /**
     * 解析系统状态信息
     */
    private void parseSystemStatus(FireAlarmEvent event, byte[] data) {
        if (data.length < 7) {
            log.error("系统状态数据长度不足");
            return;
        }

        // 系统类型
        event.setComponentType((byte) 0x00); // 0表示系统

        // 系统状态
        event.setEventType(data[0]);
        event.setComponentStatus(data[0]);

        // 系统地址
        StringBuilder address = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            address.append(String.format("%02X", data[1 + i]));
        }
        event.setComponentAddress(address.toString());

        // 事件发生时间（如果数据包含）
        if (data.length >= 13) {
            parseOccurTime(event, data, 7);
        } else {
            event.setOccurTime(LocalDateTime.now());
        }
    }

    /**
     * 解析部件状态信息
     */
    private void parseComponentStatus(FireAlarmEvent event, byte[] data) {
        if (data.length < 8) {
            log.error("部件状态数据长度不足");
            return;
        }

        // 部件类型
        event.setComponentType(data[0]);

        // 部件状态
        event.setComponentStatus(data[1]);
        
        // 根据部件状态设置事件类型
        if (data[1] == FireProtocolConstant.ComponentStatus.FIRE_ALARM) {
            event.setEventType(FireProtocolConstant.SystemStatus.FIRE_ALARM);
        } else if (data[1] == FireProtocolConstant.ComponentStatus.FAULT) {
            event.setEventType(FireProtocolConstant.SystemStatus.FAULT);
        } else {
            event.setEventType(data[1]);
        }

        // 部件地址
        StringBuilder address = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            address.append(String.format("%02X", data[2 + i]));
        }
        event.setComponentAddress(address.toString());

        // 事件发生时间（如果数据包含）
        if (data.length >= 14) {
            parseOccurTime(event, data, 8);
        } else {
            event.setOccurTime(LocalDateTime.now());
        }
    }

    /**
     * 解析事件发生时间
     */
    private void parseOccurTime(FireAlarmEvent event, byte[] data, int startIndex) {
        try {
            int year = 2000 + data[startIndex]; // 年（相对于2000年）
            int month = data[startIndex + 1]; // 月
            int day = data[startIndex + 2]; // 日
            int hour = data[startIndex + 3]; // 时
            int minute = data[startIndex + 4]; // 分
            int second = data[startIndex + 5]; // 秒

            event.setOccurTime(LocalDateTime.of(year, month, day, hour, minute, second));
        } catch (Exception e) {
            log.error("解析事件时间异常", e);
            event.setOccurTime(LocalDateTime.now());
        }
    }

    /**
     * 生成事件描述
     */
    private void generateEventDescription(FireAlarmEvent event) {
        StringBuilder description = new StringBuilder();

        // 控制器类型描述
        description.append(getControllerTypeDesc(event.getControllerType())).append("，");

        // 部件类型描述
        if (event.getComponentType() == 0) {
            description.append("系统");
        } else {
            description.append(getComponentTypeDesc(event.getComponentType()));
        }

        // 部件地址
        description.append("(地址：").append(event.getComponentAddress()).append(")，");

        // 事件状态描述
        description.append(getStatusDesc(event.getComponentStatus()));

        event.setDescription(description.toString());
    }

    /**
     * 获取控制器类型描述
     */
    private String getControllerTypeDesc(byte type) {
        switch (type) {
            case FireProtocolConstant.ControlUnitType.GENERAL:
                return "通用";
            case FireProtocolConstant.ControlUnitType.FIRE_ALARM_SYSTEM:
                return "火灾报警系统";
            case FireProtocolConstant.ControlUnitType.FIRE_LINKAGE_CONTROLLER:
                return "消防联动控制器";
            case FireProtocolConstant.ControlUnitType.HYDRANT_SYSTEM:
                return "消火栓系统";
            case FireProtocolConstant.ControlUnitType.AUTO_SPRINKLER_SYSTEM:
                return "自动喷水灭火系统";
            case FireProtocolConstant.ControlUnitType.GAS_EXTINGUISHING_SYSTEM:
                return "气体灭火系统";
            case FireProtocolConstant.ControlUnitType.WATER_SPRAY_SYSTEM_PUMP:
                return "水喷雾灭火系统(泵启动方式)";
            case FireProtocolConstant.ControlUnitType.WATER_SPRAY_SYSTEM_PRESSURE:
                return "水喷雾灭火系统(压力容器启动方式)";
            case FireProtocolConstant.ControlUnitType.FOAM_EXTINGUISHING_SYSTEM:
                return "泡沫灭火系统";
            case FireProtocolConstant.ControlUnitType.DRY_POWDER_SYSTEM:
                return "干粉灭火系统";
            case FireProtocolConstant.ControlUnitType.SMOKE_CONTROL_SYSTEM:
                return "防烟排烟系统";
            case FireProtocolConstant.ControlUnitType.FIRE_DOOR_AND_CURTAIN_SYSTEM:
                return "防火门及卷帘系统";
            case FireProtocolConstant.ControlUnitType.FIRE_ELEVATOR:
                return "消防电梯";
            case FireProtocolConstant.ControlUnitType.EMERGENCY_BROADCAST:
                return "消防应急广播";
            case FireProtocolConstant.ControlUnitType.EMERGENCY_LIGHTING:
                return "消防应急照明和疏散指示系统";
            case FireProtocolConstant.ControlUnitType.FIRE_POWER_SUPPLY:
                return "消防电源";
            case FireProtocolConstant.ControlUnitType.FIRE_TELEPHONE:
                return "消防电话";
            default:
                return "未知控制器(" + type + ")";
        }
    }

    /**
     * 获取部件类型描述
     */
    private String getComponentTypeDesc(byte type) {
        switch (type) {
            case FireProtocolConstant.ComponentType.FIRE_DETECTOR:
                return "火灾报警探测器";
            case FireProtocolConstant.ComponentType.MANUAL_ALARM_BUTTON:
                return "手动火灾报警按钮";
            case FireProtocolConstant.ComponentType.HYDRANT_BUTTON:
                return "消火栓按钮";
            case FireProtocolConstant.ComponentType.DETECTION_CIRCUIT:
                return "探测回路";
            case FireProtocolConstant.ComponentType.FIRE_DISPLAY_PANEL:
                return "火灾显示盘";
            case FireProtocolConstant.ComponentType.HEAT_DETECTOR:
                return "感温火灾探测器";
            case FireProtocolConstant.ComponentType.POINT_HEAT_DETECTOR:
                return "点型感温火灾探测器";
            case FireProtocolConstant.ComponentType.SMOKE_DETECTOR:
                return "感烟火灾探测器";
            case FireProtocolConstant.ComponentType.POINT_ION_SMOKE_DETECTOR:
                return "点型离子感烟火灾探测器";
            case FireProtocolConstant.ComponentType.POINT_PHOTOELECTRIC_SMOKE_DETECTOR:
                return "点型光电感烟火灾探测器";
            case FireProtocolConstant.ComponentType.LINEAR_BEAM_SMOKE_DETECTOR:
                return "线型光束感烟火灾探测器";
            case FireProtocolConstant.ComponentType.ASPIRATING_SMOKE_DETECTOR:
                return "吸气式感烟火灾探测器";
            case FireProtocolConstant.ComponentType.COMPOSITE_FIRE_DETECTOR:
                return "复合式火灾探测器";
            case FireProtocolConstant.ComponentType.COMPOSITE_SMOKE_HEAT_DETECTOR:
                return "复合式感烟感温火灾探测器";
            case FireProtocolConstant.ComponentType.COMPOSITE_LIGHT_HEAT_DETECTOR:
                return "复合式感光感温火灾探测器";
            case FireProtocolConstant.ComponentType.COMPOSITE_LIGHT_SMOKE_DETECTOR:
                return "复合式感光感烟火灾探测器";
            case FireProtocolConstant.ComponentType.UV_FLAME_DETECTOR:
                return "紫外火焰探测器";
            case FireProtocolConstant.ComponentType.IR_FLAME_DETECTOR:
                return "红外火焰探测器";
            case FireProtocolConstant.ComponentType.LIGHT_FIRE_DETECTOR:
                return "感光火灾探测器";
            case FireProtocolConstant.ComponentType.GAS_DETECTOR:
                return "气体探测器";
            case FireProtocolConstant.ComponentType.IMAGE_FIRE_DETECTOR:
                return "图像摄像方式火灾探测器";
            case FireProtocolConstant.ComponentType.SOUND_FIRE_DETECTOR:
                return "感声火灾探测器";
            case FireProtocolConstant.ComponentType.GAS_EXTINGUISHING_CONTROLLER:
                return "气体灭火控制器";
            case FireProtocolConstant.ComponentType.ELECTRICAL_FIRE_CONTROL:
                return "消防电气控制装置";
            case FireProtocolConstant.ComponentType.FIRE_CONTROL_DISPLAY:
                return "消防控制室图形显示装置";
            case FireProtocolConstant.ComponentType.MODULE:
                return "模块";
            case FireProtocolConstant.ComponentType.INPUT_MODULE:
                return "输入模块";
            case FireProtocolConstant.ComponentType.OUTPUT_MODULE:
                return "输出模块";
            case FireProtocolConstant.ComponentType.IO_MODULE:
                return "输入/输出模块";
            case FireProtocolConstant.ComponentType.RELAY_MODULE:
                return "中继模块";
            case FireProtocolConstant.ComponentType.FIRE_PUMP:
                return "消防水泵";
            case FireProtocolConstant.ComponentType.FIRE_WATER_TANK:
                return "消防水箱";
            case FireProtocolConstant.ComponentType.SPRINKLER_PUMP:
                return "喷淋泵";
            case FireProtocolConstant.ComponentType.WATER_FLOW_INDICATOR:
                return "水流指示器";
            case FireProtocolConstant.ComponentType.SIGNAL_VALVE:
                return "信号阀";
            case FireProtocolConstant.ComponentType.ALARM_VALVE:
                return "报警阀";
            case FireProtocolConstant.ComponentType.PRESSURE_SWITCH:
                return "压力开关";
            case FireProtocolConstant.ComponentType.VALVE_DRIVER:
                return "阀驱动装置";
            case FireProtocolConstant.ComponentType.FIRE_DOOR:
                return "防火门";
            case FireProtocolConstant.ComponentType.FIRE_VALVE:
                return "防火阀";
            case FireProtocolConstant.ComponentType.VENTILATION_AC:
                return "通风空调";
            case FireProtocolConstant.ComponentType.FOAM_PUMP:
                return "泡沫液泵";
            case FireProtocolConstant.ComponentType.PIPE_SOLENOID_VALVE:
                return "管网电磁阀";
            case FireProtocolConstant.ComponentType.SMOKE_EXHAUST_FAN:
                return "防烟排烟风机";
            case FireProtocolConstant.ComponentType.SMOKE_EXHAUST_FIRE_VALVE:
                return "排烟防火阀";
            case FireProtocolConstant.ComponentType.NORMALLY_CLOSED_AIR_INLET:
                return "常闭送风口";
            case FireProtocolConstant.ComponentType.SMOKE_EXHAUST_OUTLET:
                return "排烟口";
            case FireProtocolConstant.ComponentType.ELECTRIC_SMOKE_BARRIER:
                return "电控挡烟垂壁";
            case FireProtocolConstant.ComponentType.FIRE_CURTAIN_CONTROLLER:
                return "防火卷帘控制器";
            case FireProtocolConstant.ComponentType.FIRE_DOOR_MONITOR:
                return "防火门监控器";
            case FireProtocolConstant.ComponentType.ALARM_DEVICE:
                return "警报装置";
            default:
                return "未知部件(" + type + ")";
        }
    }

    /**
     * 获取状态描述
     */
    private String getStatusDesc(byte status) {
        switch (status) {
            case FireProtocolConstant.ComponentStatus.NORMAL:
                return "正常运行";
            case FireProtocolConstant.ComponentStatus.FIRE_ALARM:
                return "火警";
            case FireProtocolConstant.ComponentStatus.FAULT:
                return "故障";
            case FireProtocolConstant.ComponentStatus.FEEDBACK:
                return "反馈";
            case FireProtocolConstant.ComponentStatus.SHIELD:
                return "屏蔽";
            case FireProtocolConstant.ComponentStatus.SUPERVISION:
                return "监管";
            case FireProtocolConstant.ComponentStatus.START:
                return "启动";
            case FireProtocolConstant.ComponentStatus.STOP:
                return "停止";
            case FireProtocolConstant.ComponentStatus.TEST:
                return "测试";
            default:
                return "未知状态(" + status + ")";
        }
    }
}