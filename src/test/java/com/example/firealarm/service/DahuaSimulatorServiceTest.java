package com.example.firealarm.service;

import com.example.firealarm.protocol.dahua.DahuaProtocolParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DahuaSimulatorServiceTest {

    @Test
    void shouldExposeIdentifiersInStatus() throws Exception {
        DahuaSimulatorService service = new DahuaSimulatorService(new DahuaProtocolParser());
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("imei", "865176080842620");
        config.put("sn", "SN0001");
        config.put("iccid", "89860012345678901234");
        config.put("productModel", "DH-HY-SIM");
        service.updateConfig(config);

        Map<String, Object> status = service.getStatus();
        assertEquals("865176080842620", status.get("imei"));
        assertEquals("176080842620", status.get("sourceAddress"));
        assertEquals("SN0001", status.get("sn"));
        assertEquals("89860012345678901234", status.get("iccid"));
    }

    @Test
    void shouldBuildRegistrationPayloadWithConfiguredImei() throws Exception {
        DahuaSimulatorService service = new DahuaSimulatorService(new DahuaProtocolParser());
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("imei", "865176080842620");
        config.put("sn", "3C197DYAZ00072");
        config.put("iccid", "89860012345678901234");
        config.put("imsi", "460001234567890");
        service.updateConfig(config);

        Method method = DahuaSimulatorService.class.getDeclaredMethod("buildRegistrationPayload");
        method.setAccessible(true);
        byte[] payload = (byte[]) method.invoke(service);

        assertEquals((byte) 0x08, payload[20]);
        assertEquals((byte) 0x65, payload[21]);
        assertEquals((byte) 0x17, payload[22]);
        assertEquals((byte) 0x60, payload[23]);
    }
}
