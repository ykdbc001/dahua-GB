package com.example.firealarm.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DahuaFrameHistoryService {

    private static final int MAX_RECORDS = 100;

    private final List<Map<String, Object>> frames = new ArrayList<Map<String, Object>>();

    public synchronized void record(Map<String, Object> frame) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>(frame);
        copy.put("recordedAt", LocalDateTime.now().toString());
        frames.add(0, copy);
        while (frames.size() > MAX_RECORDS) {
            frames.remove(frames.size() - 1);
        }
    }

    public synchronized List<Map<String, Object>> list() {
        return new ArrayList<Map<String, Object>>(frames);
    }

    public synchronized void clear() {
        frames.clear();
    }
}
