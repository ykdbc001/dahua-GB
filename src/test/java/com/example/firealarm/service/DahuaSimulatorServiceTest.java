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
        assertEquals("865176080842620", status.get("sourceAddress"));
        assertEquals("176080842620", status.get("protocolAddress"));
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

        java.lang.reflect.Field terminalsField = DahuaSimulatorService.class.getDeclaredField("terminals");
        terminalsField.setAccessible(true);
        java.util.List<?> terminals = (java.util.List<?>) terminalsField.get(service);
        Object terminal = terminals.get(0);

        Method method = DahuaSimulatorService.class.getDeclaredMethod("buildRegistrationPayload", terminal.getClass());
        method.setAccessible(true);
        byte[] payload = (byte[]) method.invoke(service, terminal);

        assertEquals((byte) 0x08, payload[20]);
        assertEquals((byte) 0x65, payload[21]);
        assertEquals((byte) 0x17, payload[22]);
        assertEquals((byte) 0x60, payload[23]);
    }

    @Test
    void shouldAcceptMultipleTerminalsEachBoundToOneDevice() throws Exception {
        DahuaSimulatorService service = new DahuaSimulatorService(new DahuaProtocolParser());
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        java.util.List<Map<String, Object>> terminals = new java.util.ArrayList<Map<String, Object>>();

        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("imei", "865176080842620");
        first.put("sn", "SN-1");
        first.put("componentType", 97);
        first.put("componentAddress", "000000000001");
        terminals.add(first);

        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("imei", "865176080842621");
        second.put("sn", "SN-2");
        second.put("componentType", 74);
        second.put("componentAddress", "000000000002");
        terminals.add(second);

        config.put("terminals", terminals);
        service.updateConfig(config);

        Map<String, Object> status = service.getStatus();
        assertEquals(2, status.get("terminalCount"));

        java.util.List<Map<String, Object>> terminalStatus = (java.util.List<Map<String, Object>>) status.get("terminals");
        assertEquals("865176080842620", terminalStatus.get(0).get("imei"));
        assertEquals("865176080842620", terminalStatus.get(0).get("sourceAddress"));
        assertEquals("176080842620", terminalStatus.get(0).get("protocolAddress"));
        assertEquals(97, terminalStatus.get(0).get("componentType"));
        assertEquals("000000000001", terminalStatus.get(0).get("componentAddress"));
        assertEquals("865176080842621", terminalStatus.get(1).get("imei"));
        assertEquals("865176080842621", terminalStatus.get(1).get("sourceAddress"));
        assertEquals("176080842621", terminalStatus.get(1).get("protocolAddress"));
        assertEquals(74, terminalStatus.get(1).get("componentType"));
        assertEquals("000000000002", terminalStatus.get(1).get("componentAddress"));
    }
}
