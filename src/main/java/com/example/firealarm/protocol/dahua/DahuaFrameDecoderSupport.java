package com.example.firealarm.protocol.dahua;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * 大华 TCP 定制组帧（《消防终端与平台通讯国标协议》§2.1）。
 * <p>
 * 当前实现与 {@link com.example.firealarm.protocol.demo.DemoFrameDecoderSupport} 相同，便于在 LAN 上兼容
 * 与 GB/T 26875.3 一致的外层 0x40/长度大端/0x23 帧；若设备使用文档中的「控制单元小端、注册外层」等差异，
 * 请在本类中单独改写 {@link #decodeFrames}，勿改 demo 包。
 * </p>
 */
public final class DahuaFrameDecoderSupport {

    private DahuaFrameDecoderSupport() {
    }

    public static void decodeFrames(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();

        if (in.readableBytes() < 5) {
            return;
        }

        byte startMark = in.getByte(in.readerIndex());
        if (startMark != 0x40) {
            in.skipBytes(1);
            return;
        }

        int messageLength = ((in.getByte(in.readerIndex() + 1) & 0xFF) << 8)
                | (in.getByte(in.readerIndex() + 2) & 0xFF);

        if (messageLength <= 0 || messageLength > 1024) {
            in.skipBytes(1);
            return;
        }

        int totalLength = messageLength + 5;
        if (in.readableBytes() < totalLength) {
            in.resetReaderIndex();
            return;
        }

        byte endMark = in.getByte(in.readerIndex() + totalLength - 1);
        if (endMark != 0x23) {
            in.skipBytes(1);
            return;
        }

        byte[] messageData = new byte[totalLength];
        in.readBytes(messageData);
        out.add(messageData);
    }
}
