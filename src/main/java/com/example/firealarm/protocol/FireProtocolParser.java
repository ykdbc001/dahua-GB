package com.example.firealarm.protocol;

import com.example.firealarm.constant.FireProtocolConstant;
import com.example.firealarm.model.FireMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 消防协议解析器
 * 基于GB/T 26875.3-2011国标协议
 */
@Component
public class FireProtocolParser {

    private static final Logger log = LoggerFactory.getLogger(FireProtocolParser.class);

    /**
     * 解析消防协议消息
     *
     * @param data 原始字节数据
     * @return 解析后的消息对象，解析失败返回null
     */
    public FireMessage parseMessage(byte[] data) {
        try {
            if (data == null || data.length < 15) {
                log.error("消息数据为空或长度不足");
                return null;
            }

            // 检查起始符和结束符
            if (data[0] != FireProtocolConstant.START_MARK || data[data.length - 1] != FireProtocolConstant.END_MARK) {
                log.error("消息格式错误：起始符或结束符不匹配");
                return null;
            }

            // 解析消息长度
            int messageLength = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);

            // 校验消息长度
            if (messageLength != data.length - 5) { // 减去起始符、长度字段和结束符
                log.error("消息长度不匹配：期望{}，实际{}", messageLength, data.length - 5);
                return null;
            }

            // 校验和检查
            byte calculatedChecksum = calculateChecksum(data, 3, data.length - 2);
            byte receivedChecksum = data[data.length - 2];
            if (calculatedChecksum != receivedChecksum) {
                log.error("校验和错误：计算值{}，接收值{}", calculatedChecksum, receivedChecksum);
                return null;
            }

            // 解析消息内容
            FireMessage message = new FireMessage();
            message.setStartMark(data[0]);
            message.setMessageLength(messageLength);
            message.setVersion(data[3]);
            message.setSourceType(data[4]);

            // 解析源地址（6字节）
            StringBuilder sourceAddress = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sourceAddress.append(String.format("%02X", data[5 + i]));
            }
            message.setSourceAddress(sourceAddress.toString());

            message.setTargetType(data[11]);

            // 解析目标地址（6字节）
            StringBuilder targetAddress = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                targetAddress.append(String.format("%02X", data[12 + i]));
            }
            message.setTargetAddress(targetAddress.toString());

            // 解析流水号（2字节）
            int serialNumber = ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
            message.setSerialNumber(serialNumber);

            // 命令字节
            message.setCommand(data[20]);

            // 数据内容
            int dataLength = messageLength - 18; // 减去版本号到命令字节的长度
            byte[] contentData = Arrays.copyOfRange(data, 21, 21 + dataLength);
            message.setData(contentData);

            message.setChecksum(data[data.length - 2]);
            message.setEndMark(data[data.length - 1]);
            message.setRawMessage(data);

            return message;
        } catch (Exception e) {
            log.error("解析消息异常", e);
            return null;
        }
    }

    /**
     * 构建消防协议消息
     *
     * @param sourceType 源地址类型
     * @param sourceAddress 源地址
     * @param targetType 目标地址类型
     * @param targetAddress 目标地址
     * @param serialNumber 流水号
     * @param command 命令字节
     * @param data 数据内容
     * @return 构建的消息字节数组
     */
    public byte[] buildMessage(byte sourceType, String sourceAddress, byte targetType, 
                              String targetAddress, int serialNumber, byte command, byte[] data) {
        try {
            // 计算消息长度：版本号(1) + 源地址类型(1) + 源地址(6) + 目标地址类型(1) + 目标地址(6) + 流水号(2) + 命令字节(1) + 数据内容 + 校验和(1)
            int contentLength = 18 + (data != null ? data.length : 0);
            
            // 总长度：起始符(1) + 长度(2) + 内容 + 校验和(1) + 结束符(1)
            int totalLength = 5 + contentLength;
            
            ByteBuffer buffer = ByteBuffer.allocate(totalLength);
            
            // 起始符
            buffer.put(FireProtocolConstant.START_MARK);
            
            // 长度（2字节，高字节在前）
            buffer.put((byte) ((contentLength >> 8) & 0xFF));
            buffer.put((byte) (contentLength & 0xFF));
            
            // 版本号（当前为1）
            buffer.put((byte) 0x01);
            
            // 源地址类型
            buffer.put(sourceType);
            
            // 源地址（6字节）
            putHexAddress(buffer, sourceAddress);
            
            // 目标地址类型
            buffer.put(targetType);
            
            // 目标地址（6字节）
            putHexAddress(buffer, targetAddress);
            
            // 流水号（2字节，高字节在前）
            buffer.put((byte) ((serialNumber >> 8) & 0xFF));
            buffer.put((byte) (serialNumber & 0xFF));
            
            // 命令字节
            buffer.put(command);
            
            // 数据内容
            if (data != null && data.length > 0) {
                buffer.put(data);
            }
            
            // 计算校验和（从版本号开始到数据内容结束）
            byte[] tempArray = buffer.array();
            byte checksum = calculateChecksum(tempArray, 3, 3 + contentLength);
            
            // 校验和
            buffer.put(checksum);
            
            // 结束符
            buffer.put(FireProtocolConstant.END_MARK);
            
            return buffer.array();
        } catch (Exception e) {
            log.error("构建消息异常", e);
            return null;
        }
    }

    /**
     * 计算校验和（从起始位置到结束位置的字节异或值）
     */
    private byte calculateChecksum(byte[] data, int startIndex, int endIndex) {
        byte checksum = 0;
        for (int i = startIndex; i < endIndex; i++) {
            checksum ^= data[i]; // 异或运算
        }
        return checksum;
    }

    /**
     * 将16进制字符串地址转换为字节并放入缓冲区
     */
    private void putHexAddress(ByteBuffer buffer, String address) {
        // 确保地址长度为12（6字节）
        String paddedAddress = String.format("%12s", address).replace(' ', '0');
        for (int i = 0; i < 6; i++) {
            int startIdx = i * 2;
            String byteStr = paddedAddress.substring(startIdx, startIdx + 2);
            buffer.put((byte) Integer.parseInt(byteStr, 16));
        }
    }
}