package com.example.firealarm.protocol.demo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Demo 模式：按 GB/T 26875.3 常见应用层帧从字节流中切帧（与仓库原 FireAlarmServer 内嵌解码一致）。
 */
public final class DemoFrameDecoderSupport {

    private DemoFrameDecoderSupport() {
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
