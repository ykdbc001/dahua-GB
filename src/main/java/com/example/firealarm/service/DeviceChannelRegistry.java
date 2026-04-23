package com.example.firealarm.service;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DeviceChannelRegistry {

    private final ConcurrentMap<String, Channel> deviceChannels = new ConcurrentHashMap<>();

    public ConcurrentMap<String, Channel> map() {
        return deviceChannels;
    }

    public void put(String deviceAddress, Channel channel) {
        deviceChannels.put(deviceAddress, channel);
    }

    public Channel get(String deviceAddress) {
        return deviceChannels.get(deviceAddress);
    }

    public void removeIfChannel(Channel ch) {
        deviceChannels.values().removeIf(c -> c != null && c.equals(ch));
    }
}
