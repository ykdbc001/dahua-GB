package com.example.firealarm.server;

import com.example.firealarm.protocol.ProtocolMode;
import com.example.firealarm.protocol.dahua.DahuaFrameDecoderSupport;
import com.example.firealarm.protocol.demo.DemoFrameDecoderSupport;
import com.example.firealarm.service.ProtocolModeService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 按 {@link ProtocolModeService} 选择 demo / 大华 组帧逻辑。
 */
public class ProtocolModeFrameDecoder extends ByteToMessageDecoder {

    private final ProtocolModeService protocolModeService;

    public ProtocolModeFrameDecoder(ProtocolModeService protocolModeService) {
        this.protocolModeService = protocolModeService;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (protocolModeService.getMode() == ProtocolMode.DAHUA) {
            DahuaFrameDecoderSupport.decodeFrames(ctx, in, out);
        } else {
            DemoFrameDecoderSupport.decodeFrames(ctx, in, out);
        }
    }
}
