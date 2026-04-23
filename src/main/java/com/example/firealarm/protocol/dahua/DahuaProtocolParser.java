package com.example.firealarm.protocol.dahua;

import com.example.firealarm.constant.FireProtocolConstant;
import com.example.firealarm.model.FireMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 大华定制解析器。
 *
 * <p>当前实现独立处理两类和 PDF 一致的关键差异：
 * 1. 地址按大华设备地址规则编解码；
 * 2. 业务流水号按小端处理；
 * 3. 注册帧会额外解析出 IMEI/SN/ICCID/IMSI 等字段，便于接口展示。</p>
 */
@Component
public class DahuaProtocolParser {

    private static final Logger log = LoggerFactory.getLogger(DahuaProtocolParser.class);

    public FireMessage parseMessage(byte[] data) {
        try {
            if (data == null || data.length < 15) {
                log.error("[大华] 消息数据为空或长度不足");
                return null;
            }

            if (data[0] != FireProtocolConstant.START_MARK || data[data.length - 1] != FireProtocolConstant.END_MARK) {
                log.error("[大华] 消息格式错误：起始符或结束符不匹配");
                return null;
            }

            int messageLength = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
            if (messageLength != data.length - 5) {
                log.error("[大华] 消息长度不匹配：期望{}，实际{}", messageLength, data.length - 5);
                return null;
            }

            byte calculatedChecksum = calculateChecksum(data, 3, data.length - 2);
            byte receivedChecksum = data[data.length - 2];
            if (calculatedChecksum != receivedChecksum) {
                log.error("[大华] 校验和错误：计算值={}，接收值={}", calculatedChecksum, receivedChecksum);
                return null;
            }

            FireMessage message = new FireMessage();
            message.setStartMark(data[0]);
            message.setMessageLength(messageLength);
            message.setVersion(data[3]);
            message.setSourceType(data[4]);
            message.setSourceAddress(DahuaAddressCodec.decodeAddress(Arrays.copyOfRange(data, 5, 11)));
            message.setTargetType(data[11]);
            message.setTargetAddress(DahuaAddressCodec.decodeAddress(Arrays.copyOfRange(data, 12, 18)));
            message.setSerialNumber((data[18] & 0xFF) | ((data[19] & 0xFF) << 8));
            message.setCommand(data[20]);

            int dataLength = messageLength - 18;
            message.setData(Arrays.copyOfRange(data, 21, 21 + dataLength));
            message.setChecksum(receivedChecksum);
            message.setEndMark(data[data.length - 1]);
            message.setRawMessage(data);
            message.setProtocolDetails(parseProtocolDetails(message));
            return message;
        } catch (Exception e) {
            log.error("[大华] 解析消息异常", e);
            return null;
        }
    }

    public byte[] buildMessage(byte sourceType, String sourceAddress, byte targetType,
                               String targetAddress, int serialNumber, byte command, byte[] data) {
        try {
            int contentLength = 18 + (data != null ? data.length : 0);
            int totalLength = 5 + contentLength;

            ByteBuffer buffer = ByteBuffer.allocate(totalLength);
            buffer.put(FireProtocolConstant.START_MARK);
            buffer.put((byte) ((contentLength >> 8) & 0xFF));
            buffer.put((byte) (contentLength & 0xFF));
            buffer.put((byte) 0x01);
            buffer.put(sourceType);
            buffer.put(DahuaAddressCodec.encodeAddress(sourceAddress));
            buffer.put(targetType);
            buffer.put(DahuaAddressCodec.encodeAddress(targetAddress));
            buffer.put((byte) (serialNumber & 0xFF));
            buffer.put((byte) ((serialNumber >> 8) & 0xFF));
            buffer.put(command);

            if (data != null && data.length > 0) {
                buffer.put(data);
            }

            byte[] tempArray = buffer.array();
            byte checksum = calculateChecksum(tempArray, 3, 3 + contentLength);
            buffer.put(checksum);
            buffer.put(FireProtocolConstant.END_MARK);
            return buffer.array();
        } catch (Exception e) {
            log.error("[大华] 构建消息异常", e);
            return null;
        }
    }

    private byte calculateChecksum(byte[] data, int startIndex, int endIndex) {
        byte checksum = 0;
        for (int i = startIndex; i < endIndex; i++) {
            checksum ^= data[i];
        }
        return checksum;
    }

    private Map<String, Object> parseProtocolDetails(FireMessage message) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("sourceAddress", message.getSourceAddress());
        details.put("targetAddress", message.getTargetAddress());
        details.put("serialNumber", message.getSerialNumber());
        details.put("commandHex", String.format(Locale.ROOT, "%02X", message.getCommand() & 0xFF));
        details.put("payloadHex", toHex(message.getData()));

        if ((message.getCommand() & 0xFF) == 0x00) {
            details.put("frameType", "register");
            details.putAll(parseRegistrationPayload(message.getData()));
        } else if ((message.getCommand() & 0xFF) == (FireProtocolConstant.CommandType.UPLOAD_SYSTEM_STATUS & 0xFF)) {
            details.put("frameType", "systemStatus");
        } else if ((message.getCommand() & 0xFF) == (FireProtocolConstant.CommandType.UPLOAD_COMPONENT_STATUS & 0xFF)) {
            details.put("frameType", "componentStatus");
        } else {
            details.put("frameType", "other");
        }
        return details;
    }

    private Map<String, Object> parseRegistrationPayload(byte[] payload) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<String, Object>();
        if (payload == null || payload.length < 62) {
            details.put("registerPayloadValid", false);
            return details;
        }

        details.put("registerPayloadValid", true);
        details.put("protocolVersion", toVersion(payload, 0));
        details.put("softwareVersion", toVersion(payload, 2));
        details.put("sn", readAscii(payload, 4, 16));
        details.put("imei", readBcd(payload, 20, 8));
        details.put("imsi", readBcd(payload, 28, 8));
        details.put("iccid", readBcd(payload, 36, 10));
        details.put("keepaliveSeconds", readUIntLe(payload, 46, 4));
        details.put("productModel", readAscii(payload, 50, 16));
        return details;
    }

    private static String toVersion(byte[] payload, int offset) {
        return "v" + (payload[offset] & 0xFF) + "." + String.format(Locale.ROOT, "%02d", payload[offset + 1] & 0xFF);
    }

    private static String readAscii(byte[] payload, int offset, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len && offset + i < payload.length; i++) {
            byte b = payload[offset + i];
            if (b == 0x00) {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString().trim();
    }

    private static String readBcd(byte[] payload, int offset, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len && offset + i < payload.length; i++) {
            int value = payload[offset + i] & 0xFF;
            sb.append((value >> 4) & 0x0F).append(value & 0x0F);
        }
        while (sb.length() > 1 && sb.charAt(0) == '0') {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    private static long readUIntLe(byte[] payload, int offset, int len) {
        long value = 0;
        for (int i = 0; i < len && offset + i < payload.length; i++) {
            value |= ((long) payload[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02X", b));
        }
        return sb.toString();
    }
}
