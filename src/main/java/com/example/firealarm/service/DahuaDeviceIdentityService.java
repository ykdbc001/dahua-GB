package com.example.firealarm.service;

import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DahuaDeviceIdentityService {

    private final Map<String, String> channelIdToImei = new ConcurrentHashMap<String, String>();
    private final Map<String, String> protocolAddressToImei = new ConcurrentHashMap<String, String>();

    public void bind(Channel channel, String protocolAddress, String imei) {
        if (channel != null && imei != null && !imei.trim().isEmpty()) {
            channelIdToImei.put(channel.id().asLongText(), imei);
        }
        if (protocolAddress != null && imei != null && !imei.trim().isEmpty()) {
            protocolAddressToImei.put(protocolAddress, imei);
        }
    }

    public String resolve(Channel channel, String protocolAddress) {
        if (channel != null) {
            String imei = channelIdToImei.get(channel.id().asLongText());
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }
        }
        if (protocolAddress != null) {
            return protocolAddressToImei.get(protocolAddress);
        }
        return null;
    }

    public void remove(Channel channel) {
        if (channel != null) {
            channelIdToImei.remove(channel.id().asLongText());
        }
    }
}
