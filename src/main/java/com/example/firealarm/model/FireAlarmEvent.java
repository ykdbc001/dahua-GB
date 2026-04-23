package com.example.firealarm.model;

import java.time.LocalDateTime;

/**
 * 消防报警事件实体类
 */
public class FireAlarmEvent {

    private String id;
    private String deviceAddress;
    private byte controllerType;
    private byte eventType;
    private byte componentType;
    private String componentAddress;
    private byte componentStatus;
    private String description;
    private LocalDateTime occurTime;
    private LocalDateTime receiveTime;
    private FireMessage originalMessage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public void setDeviceAddress(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    public byte getControllerType() {
        return controllerType;
    }

    public void setControllerType(byte controllerType) {
        this.controllerType = controllerType;
    }

    public byte getEventType() {
        return eventType;
    }

    public void setEventType(byte eventType) {
        this.eventType = eventType;
    }

    public byte getComponentType() {
        return componentType;
    }

    public void setComponentType(byte componentType) {
        this.componentType = componentType;
    }

    public String getComponentAddress() {
        return componentAddress;
    }

    public void setComponentAddress(String componentAddress) {
        this.componentAddress = componentAddress;
    }

    public byte getComponentStatus() {
        return componentStatus;
    }

    public void setComponentStatus(byte componentStatus) {
        this.componentStatus = componentStatus;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
    }

    public LocalDateTime getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime) {
        this.receiveTime = receiveTime;
    }

    public FireMessage getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(FireMessage originalMessage) {
        this.originalMessage = originalMessage;
    }
}
