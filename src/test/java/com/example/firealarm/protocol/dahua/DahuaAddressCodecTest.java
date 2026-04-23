package com.example.firealarm.protocol.dahua;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DahuaAddressCodecTest {

    @Test
    void shouldDecodeBcdDeviceAddressTo12DigitString() {
        byte[] raw = new byte[]{0x17, 0x60, (byte) 0x80, (byte) 0x84, 0x26, 0x20};

        assertEquals("176080842620", DahuaAddressCodec.decodeAddress(raw));
    }

    @Test
    void shouldEncode15DigitImeiUsingLast12Digits() {
        byte[] encoded = DahuaAddressCodec.encodeAddress("865176080842620");

        assertArrayEquals(new byte[]{0x17, 0x60, (byte) 0x80, (byte) 0x84, 0x26, 0x20}, encoded);
    }

    @Test
    void shouldPreservePlatformAddressMarkers() {
        assertArrayEquals(
                new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                DahuaAddressCodec.encodeAddress("FFFFFFFFFFFF"));
        assertEquals("000000000000", DahuaAddressCodec.decodeAddress(new byte[6]));
    }
}
