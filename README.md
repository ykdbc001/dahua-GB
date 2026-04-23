# 消防报警系统

## 项目介绍

本项目是基于GB/T 26875.3-2011国标协议实现的消防设备接入系统，主要功能是通过主机接收消防设备的报警信息，并实时向用户提供火警等报警信息。

## 技术栈

- Java 8
- Spring Boot 2.7.5
- Netty 4.1.86.Final（TCP服务器实现）
- WebSocket（实时消息推送）
- Bootstrap 5（前端UI）

## 系统架构

系统主要由以下几个部分组成：

1. **消防协议解析模块**：负责解析和构建符合GB/T 26875.3-2011标准的消防协议消息
2. **TCP服务器模块**：基于Netty实现，用于接收消防设备的TCP连接和消息
3. **事件处理模块**：处理消防设备上报的各类事件，如火警、故障等
4. **实时通知模块**：通过WebSocket向前端实时推送报警事件
5. **Web界面**：展示系统状态和报警事件的用户界面
6. **模拟器**：用于测试的消防设备模拟器

## 项目结构

```
src/main/java/com/example/firealarm/
├── FireAlarmApplication.java        # 应用程序入口
├── config/
│   └── WebSocketConfig.java         # WebSocket配置
├── constant/
│   └── FireProtocolConstant.java    # 消防协议常量
├── controller/
│   └── FireAlarmController.java     # REST API控制器
├── model/
│   ├── FireAlarmEvent.java          # 报警事件实体类
│   └── FireMessage.java             # 消防协议消息实体类
├── protocol/
│   └── FireProtocolParser.java      # 消防协议解析器
├── server/
│   └── FireAlarmServer.java         # 基于Netty的TCP服务器
├── service/
│   ├── FireAlarmEventHandler.java   # 报警事件处理器
│   └── FireAlarmNotificationService.java # 报警通知服务
└── test/
    └── FireAlarmSimulator.java      # 消防设备模拟器
```

## 功能特点

1. **协议解析**：完整实现GB/T 26875.3-2011国标协议的解析和构建
2. **实时监控**：实时接收和处理消防设备上报的各类事件
3. **多设备支持**：支持多个消防设备同时连接
4. **实时通知**：通过WebSocket实时向前端推送报警事件
5. **友好界面**：直观展示系统状态和报警事件
6. **模拟测试**：内置消防设备模拟器，方便测试

## 使用方法

### 1. 环境要求

- JDK 1.8+
- Maven 3.6+

### 2. 编译运行

```bash
# 克隆项目
git clone [项目地址]
cd fire-alarm-system

# 编译打包
mvn clean package

# 运行
java -jar target/fire-alarm-system-0.0.1-SNAPSHOT.jar
```

### 3. 配置说明

主要配置项在`application.yml`文件中：

```yaml
server:
  port: 8080  # Web服务端口

fire-alarm:
  server:
    port: 9000  # 消防设备连接端口
    read-timeout: 60000  # 读取超时时间(毫秒)
    idle-timeout: 180000  # 空闲超时时间(毫秒)
```

### 4. 访问系统

启动应用后，通过浏览器访问：`http://localhost:8080`

## 消防协议说明

本系统实现的GB/T 26875.3-2011国标协议主要特点：

1. **消息格式**：起始符(0x40) + 长度(2字节) + 内容 + 校验和(1字节) + 结束符(0x23)
2. **内容格式**：版本号(1字节) + 源地址类型(1字节) + 源地址(6字节) + 目标地址类型(1字节) + 目标地址(6字节) + 流水号(2字节) + 命令字节(1字节) + 数据内容
3. **校验方式**：从版本号到数据内容的所有字节进行异或运算

## 测试说明

系统内置了一个消防设备模拟器（FireAlarmSimulator），启动应用后会自动连接到服务器并定期发送测试消息，包括：

1. 定期发送正常状态消息
2. 随机发送火警和故障事件
3. 事件发送后会在一定时间后自动恢复正常

## 扩展开发

如需扩展系统功能，可以从以下几个方面入手：

1. 增加更多的消防协议命令支持
2. 实现数据持久化，存储历史报警记录
3. 增加用户管理和权限控制
4. 增加报警联动功能，如短信、邮件通知
5. 增加设备管理功能，如设备注册、配置等

## 注意事项

1. 本系统仅用于演示和学习，实际应用需要进行更严格的测试和安全性评估
2. 实际部署时，建议配置适当的网络安全措施，如防火墙、VPN等
3. 消防系统关系到生命财产安全，实际应用时需要符合相关法规和标准