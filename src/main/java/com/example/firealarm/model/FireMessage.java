package com.example.firealarm.model;

import java.util.Map;

/**
 * 消防协议消息实体类
 * 基于GB/T 26875.3-2011国标协议
 */
public class FireMessage {

    private byte startMark;
    private int messageLength;
    private byte version;
    private byte sourceType;
    private String sourceAddress;
    private byte targetType;
    private String targetAddress;
    private int serialNumber;
    private byte command;
    private byte[] data;
    private byte checksum;
    private byte endMark;
    private byte[] rawMessage;
    private Map<String, Object> protocolDetails;

    public byte getStartMark() {
        return startMark;
    }

    public void setStartMark(byte startMark) {
        this.startMark = startMark;
    }

    public int getMessageLength() {
        return messageLength;
    }

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getSourceType() {
        return sourceType;
    }

    public void setSourceType(byte sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public byte getTargetType() {
        return targetType;
    }

    public void setTargetType(byte targetType) {
        this.targetType = targetType;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }

    public int getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(int serialNumber) {
        this.serialNumber = serialNumber;
    }

    public byte getCommand() {
        return command;
    }

    public void setCommand(byte command) {
        this.command = command;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte getChecksum() {
        return checksum;
    }

    public void setChecksum(byte checksum) {
        this.checksum = checksum;
    }

    public byte getEndMark() {
        return endMark;
    }

    public void setEndMark(byte endMark) {
        this.endMark = endMark;
    }

    public byte[] getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(byte[] rawMessage) {
        this.rawMessage = rawMessage;
    }

    public Map<String, Object> getProtocolDetails() {
        return protocolDetails;
    }

    public void setProtocolDetails(Map<String, Object> protocolDetails) {
        this.protocolDetails = protocolDetails;
    }
}
