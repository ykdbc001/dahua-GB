package com.example.firealarm.protocol;

/**
 * TCP 9000 解析/应答策略（与《消防终端与平台通讯国标协议》大华定制可独立演进）。
 */
public enum ProtocolMode {
    /** 当前仓库原有 GB/T 26875.3 风格 demo */
    DEMO,
    /** 大华 TCP 定制（帧拆包与应答逻辑在 {@code dahua} 包内单独维护） */
    DAHUA
}
