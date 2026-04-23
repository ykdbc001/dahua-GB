package com.example.firealarm.protocol.dahua;

import com.example.firealarm.model.FireMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DahuaProtocolParserTest {

    private final DahuaProtocolParser parser = new DahuaProtocolParser();

    @Test
    void shouldParseDeviceAddressAsImeiLast12DigitsAndSerialAsLittleEndian() {
        byte[] frame = parser.buildMessage(
                (byte) 0x01,
                "865176080842620",
                (byte) 0x00,
                "FFFFFFFFFFFF",
                0x1234,
                (byte) 0x02,
                new byte[]{0x61, 0x02, 0x11, 0x22, 0x33}
        );

        FireMessage message = parser.parseMessage(frame);

        assertNotNull(message);
        assertEquals("176080842620", message.getSourceAddress());
        assertEquals("FFFFFFFFFFFF", message.getTargetAddress());
        assertEquals(0x1234, message.getSerialNumber());
        assertEquals(0x02, message.getCommand() & 0xFF);
        assertArrayEquals(new byte[]{0x61, 0x02, 0x11, 0x22, 0x33}, message.getData());
    }
}
