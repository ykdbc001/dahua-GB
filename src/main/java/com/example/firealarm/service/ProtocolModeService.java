package com.example.firealarm.service;

import com.example.firealarm.protocol.ProtocolMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProtocolModeService {

    private final AtomicReference<ProtocolMode> mode = new AtomicReference<>();

    public ProtocolModeService(@Value("${fire-alarm.protocol-mode:demo}") String initial) {
        mode.set(parse(initial));
    }

    private static ProtocolMode parse(String s) {
        if (s == null) {
            return ProtocolMode.DEMO;
        }
        String n = s.trim().toLowerCase();
        if ("dahua".equals(n) || "大华".equals(n)) {
            return ProtocolMode.DAHUA;
        }
        return ProtocolMode.DEMO;
    }

    public ProtocolMode getMode() {
        return mode.get();
    }

    public void setMode(ProtocolMode m) {
        if (m != null) {
            mode.set(m);
        }
    }

    public void setModeFromString(String s) {
        if (s == null) {
            return;
        }
        String n = s.trim().toLowerCase();
        if ("dahua".equals(n) || "大华".equals(n)) {
            mode.set(ProtocolMode.DAHUA);
        } else if ("demo".equals(n) || "国标".equals(n) || "gb".equals(n)) {
            mode.set(ProtocolMode.DEMO);
        }
    }
}
