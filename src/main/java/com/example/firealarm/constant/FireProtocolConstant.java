package com.example.firealarm.constant;

/**
 * 消防协议常量类
 * 基于GB/T 26875.3-2011国标协议
 */
public class FireProtocolConstant {

    /**
     * 协议起始符
     */
    public static final byte START_MARK = 0x40;  // '@'

    /**
     * 协议结束符
     */
    public static final byte END_MARK = 0x23;  // '#'

    /**
     * 控制单元类型（系统类型）
     * 根据GB/T 26875.3-2011国标协议表4系统类型定义
     */
    public static class ControlUnitType {
        // 通用
        public static final byte GENERAL = 0x00;  // 0
        // 火灾报警系统
        public static final byte FIRE_ALARM_SYSTEM = 0x01;  // 1
        // 消防联动控制器
        public static final byte FIRE_LINKAGE_CONTROLLER = 0x0A;  // 10
        // 消火栓系统
        public static final byte HYDRANT_SYSTEM = 0x0B;  // 11
        // 自动喷水灭火系统
        public static final byte AUTO_SPRINKLER_SYSTEM = 0x0C;  // 12
        // 气体灭火系统
        public static final byte GAS_EXTINGUISHING_SYSTEM = 0x0D;  // 13
        // 水喷雾灭火系统(泵启动方式)
        public static final byte WATER_SPRAY_SYSTEM_PUMP = 0x0E;  // 14
        // 水喷雾灭火系统(压力容器启动方式)
        public static final byte WATER_SPRAY_SYSTEM_PRESSURE = 0x0F;  // 15
        // 泡沫灭火系统
        public static final byte FOAM_EXTINGUISHING_SYSTEM = 0x10;  // 16
        // 干粉灭火系统
        public static final byte DRY_POWDER_SYSTEM = 0x11;  // 17
        // 防烟排烟系统
        public static final byte SMOKE_CONTROL_SYSTEM = 0x12;  // 18
        // 防火门及卷帘系统
        public static final byte FIRE_DOOR_AND_CURTAIN_SYSTEM = 0x13;  // 19
        // 消防电梯
        public static final byte FIRE_ELEVATOR = 0x14;  // 20
        // 消防应急广播
        public static final byte EMERGENCY_BROADCAST = 0x15;  // 21
        // 消防应急照明和疏散指示系统
        public static final byte EMERGENCY_LIGHTING = 0x16;  // 22
        // 消防电源
        public static final byte FIRE_POWER_SUPPLY = 0x17;  // 23
        // 消防电话
        public static final byte FIRE_TELEPHONE = 0x18;  // 24
    }


    /**
     * 命令类型
     */
    public static class CommandType {
        // 上传建筑消防设施系统状态
        public static final byte UPLOAD_SYSTEM_STATUS = 0x01;
        // 上传建筑消防设施部件运行状态
        public static final byte UPLOAD_COMPONENT_STATUS = 0x02;
        // 上传建筑消防设施部件模拟量值
        public static final byte UPLOAD_ANALOG_VALUE = 0x03;
        // 上传建筑消防设施操作信息
        public static final byte UPLOAD_OPERATION_INFO = 0x04;
        // 上传建筑消防设施软件版本
        public static final byte UPLOAD_SOFTWARE_VERSION = 0x05;
        // 上传建筑消防设施系统配置情况
        public static final byte UPLOAD_SYSTEM_CONFIG = 0x06;
        // 上传建筑消防设施部件配置情况
        public static final byte UPLOAD_COMPONENT_CONFIG = 0x07;
        // 上传建筑消防设施系统时间
        public static final byte UPLOAD_SYSTEM_TIME = 0x08;
        // 接收建筑消防设施系统时间
        public static final byte RECEIVE_SYSTEM_TIME = 0x09;
        // 接收建筑消防设施部件操作
        public static final byte RECEIVE_COMPONENT_OPERATION = 0x0A;
        // 接收建筑消防设施系统控制
        public static final byte RECEIVE_SYSTEM_CONTROL = 0x0B;
    }

    /**
     * 系统状态
     */
    public static class SystemStatus {
        // 正常状态
        public static final byte NORMAL = 0x00;
        // 火警状态
        public static final byte FIRE_ALARM = 0x01;
        // 故障状态
        public static final byte FAULT = 0x02;
        // 屏蔽状态
        public static final byte SHIELD = 0x03;
        // 监管状态
        public static final byte SUPERVISION = 0x04;
        // 测试状态
        public static final byte TEST = 0x05;
    }

    /**
     * 部件类型
     * 根据GB/T 26875.3-2011国标协议
     */
    public static class ComponentType {
        // 火灾报警探测器
        public static final byte FIRE_DETECTOR = 0x19; // 25
        // 手动火灾报警按钮
        public static final byte MANUAL_ALARM_BUTTON = 0x17; // 23
        // 消火栓按钮
        public static final byte HYDRANT_BUTTON = 0x18; // 24
        // 探测回路
        public static final byte DETECTION_CIRCUIT = 0x15; // 21
        // 火灾显示盘
        public static final byte FIRE_DISPLAY_PANEL = 0x16; // 22
        // 感温火灾探测器
        public static final byte HEAT_DETECTOR = 0x1E; // 30
        // 点型感温火灾探测器
        public static final byte POINT_HEAT_DETECTOR = 0x1F; // 31
        // 感烟火灾探测器
        public static final byte SMOKE_DETECTOR = 0x28; // 40
        // 点型离子感烟火灾探测器
        public static final byte POINT_ION_SMOKE_DETECTOR = 0x29; // 41
        // 点型光电感烟火灾探测器
        public static final byte POINT_PHOTOELECTRIC_SMOKE_DETECTOR = 0x2A; // 42
        // 线型光束感烟火灾探测器
        public static final byte LINEAR_BEAM_SMOKE_DETECTOR = 0x2B; // 43
        // 吸气式感烟火灾探测器
        public static final byte ASPIRATING_SMOKE_DETECTOR = 0x2C; // 44
        // 复合式火灾探测器
        public static final byte COMPOSITE_FIRE_DETECTOR = 0x32; // 50
        // 复合式感烟感温火灾探测器
        public static final byte COMPOSITE_SMOKE_HEAT_DETECTOR = 0x33; // 51
        // 复合式感光感温火灾探测器
        public static final byte COMPOSITE_LIGHT_HEAT_DETECTOR = 0x34; // 52
        // 复合式感光感烟火灾探测器
        public static final byte COMPOSITE_LIGHT_SMOKE_DETECTOR = 0x35; // 53
        // 紫外火焰探测器
        public static final byte UV_FLAME_DETECTOR = 0x3D; // 61
        // 红外火焰探测器
        public static final byte IR_FLAME_DETECTOR = 0x3E; // 62
        // 感光火灾探测器
        public static final byte LIGHT_FIRE_DETECTOR = 0x45; // 69
        // 气体探测器
        public static final byte GAS_DETECTOR = 0x4A; // 74
        // 图像摄像方式火灾探测器
        public static final byte IMAGE_FIRE_DETECTOR = 0x4E; // 78
        // 感声火灾探测器
        public static final byte SOUND_FIRE_DETECTOR = 0x4F; // 79
        // 气体灭火控制器
        public static final byte GAS_EXTINGUISHING_CONTROLLER = 0x51; // 81
        // 消防电气控制装置
        public static final byte ELECTRICAL_FIRE_CONTROL = 0x52; // 82
        // 消防控制室图形显示装置
        public static final byte FIRE_CONTROL_DISPLAY = 0x53; // 83
        // 模块
        public static final byte MODULE = 0x54; // 84
        // 输入模块
        public static final byte INPUT_MODULE = 0x55; // 85
        // 输出模块
        public static final byte OUTPUT_MODULE = 0x56; // 86
        // 输入/输出模块
        public static final byte IO_MODULE = 0x57; // 87
        // 中继模块
        public static final byte RELAY_MODULE = 0x58; // 88
        // 消防水泵
        public static final byte FIRE_PUMP = 0x5B; // 91
        // 消防水箱
        public static final byte FIRE_WATER_TANK = 0x5C; // 92
        // 喷淋泵
        public static final byte SPRINKLER_PUMP = 0x5F; // 95
        // 水流指示器
        public static final byte WATER_FLOW_INDICATOR = 0x60; // 96
        // 信号阀
        public static final byte SIGNAL_VALVE = 0x61; // 97
        // 报警阀
        public static final byte ALARM_VALVE = 0x62; // 98
        // 压力开关
        public static final byte PRESSURE_SWITCH = 0x63; // 99
        // 阀驱动装置
        public static final byte VALVE_DRIVER = 0x65; // 101
        // 防火门
        public static final byte FIRE_DOOR = 0x66; // 102
        // 防火阀
        public static final byte FIRE_VALVE = 0x67; // 103
        // 通风空调
        public static final byte VENTILATION_AC = 0x68; // 104
        // 泡沫液泵
        public static final byte FOAM_PUMP = 0x69; // 105
        // 管网电磁阀
        public static final byte PIPE_SOLENOID_VALVE = 0x6A; // 106
        // 防烟排烟风机
        public static final byte SMOKE_EXHAUST_FAN = 0x6F; // 111
        // 排烟防火阀
        public static final byte SMOKE_EXHAUST_FIRE_VALVE = 0x71; // 113
        // 常闭送风口
        public static final byte NORMALLY_CLOSED_AIR_INLET = 0x72; // 114
        // 排烟口
        public static final byte SMOKE_EXHAUST_OUTLET = 0x73; // 115
        // 电控挡烟垂壁
        public static final byte ELECTRIC_SMOKE_BARRIER = 0x74; // 116
        // 防火卷帘控制器
        public static final byte FIRE_CURTAIN_CONTROLLER = 0x75; // 117
        // 防火门监控器
        public static final byte FIRE_DOOR_MONITOR = 0x76; // 118
        // 警报装置
        public static final byte ALARM_DEVICE = 0x79; // 121
    }

    /**
     * 部件状态
     */
    public static class ComponentStatus {
        // 正常运行
        public static final byte NORMAL = 0x00;
        // 火警
        public static final byte FIRE_ALARM = 0x01;
        // 故障
        public static final byte FAULT = 0x02;
        // 反馈
        public static final byte FEEDBACK = 0x03;
        // 屏蔽
        public static final byte SHIELD = 0x04;
        // 监管
        public static final byte SUPERVISION = 0x05;
        // 启动
        public static final byte START = 0x06;
        // 停止
        public static final byte STOP = 0x07;
        // 测试
        public static final byte TEST = 0x08;
    }
}