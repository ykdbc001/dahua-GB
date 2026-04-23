package com.example.firealarm.protocol.dahua;

import java.util.Locale;

/**
 * 大华定制地址编解码。
 *
 * <p>根据协议文档：
 * 1. 源地址/目的地址为设备地址时，可取设备 SN/IMEI 等标识的后 12 位；
 * 2. 地址按大端格式传输；
 * 3. 平台地址通常为全 F，也允许填 0。</p>
 *
 * <p>当前实现优先支持最常见的 IMEI/数字地址场景：
 * 12 位纯数字地址按 BCD 6B 编解码；15 位 IMEI 在编码时自动截取后 12 位。
 * 若地址无法按十进制地址解释，则回退为 12 位 HEX 字符串。</p>
 */
public final class DahuaAddressCodec {

    private static final int ADDRESS_BYTES = 6;

    private DahuaAddressCodec() {
    }

    public static String decodeAddress(byte[] rawAddress) {
        if (rawAddress == null || rawAddress.length != ADDRESS_BYTES) {
            throw new IllegalArgumentException("Dahua address must be 6 bytes");
        }

        if (isFilledWith(rawAddress, (byte) 0xFF)) {
            return "FFFFFFFFFFFF";
        }
        if (isFilledWith(rawAddress, (byte) 0x00)) {
            return "000000000000";
        }
        if (isBcdDigits(rawAddress)) {
            return decodeBcdDigits(rawAddress);
        }
        return toHex(rawAddress);
    }

    public static byte[] encodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return new byte[ADDRESS_BYTES];
        }

        String normalized = address.trim().toUpperCase(Locale.ROOT);
        if ("FFFFFFFFFFFF".equals(normalized)) {
            return filled((byte) 0xFF);
        }
        if ("000000000000".equals(normalized)) {
            return new byte[ADDRESS_BYTES];
        }

        String digitsOnly = normalized.replaceAll("\\D", "");
        if (!digitsOnly.isEmpty()) {
            return encodeDecimalAddress(digitsOnly);
        }

        String hexOnly = normalized.replaceAll("[^0-9A-F]", "");
        if (hexOnly.length() > 12) {
            hexOnly = hexOnly.substring(hexOnly.length() - 12);
        }
        hexOnly = leftPad(hexOnly, 12, '0');
        return hexToBytes(hexOnly);
    }

    static byte[] encodeDecimalAddress(String digits) {
        String last12Digits = digits.length() > 12 ? digits.substring(digits.length() - 12) : digits;
        String padded = leftPad(last12Digits, 12, '0');
        byte[] result = new byte[ADDRESS_BYTES];
        for (int i = 0; i < ADDRESS_BYTES; i++) {
            int hi = padded.charAt(i * 2) - '0';
            int lo = padded.charAt(i * 2 + 1) - '0';
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }

    private static boolean isFilledWith(byte[] bytes, byte value) {
        for (byte b : bytes) {
            if (b != value) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBcdDigits(byte[] bytes) {
        for (byte b : bytes) {
            int hi = (b >> 4) & 0x0F;
            int lo = b & 0x0F;
            if (hi > 9 || lo > 9) {
                return false;
            }
        }
        return true;
    }

    private static String decodeBcdDigits(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append((b >> 4) & 0x0F).append(b & 0x0F);
        }
        return sb.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[ADDRESS_BYTES];
        for (int i = 0; i < ADDRESS_BYTES; i++) {
            int start = i * 2;
            result[i] = (byte) Integer.parseInt(hex.substring(start, start + 2), 16);
        }
        return result;
    }

    private static byte[] filled(byte value) {
        byte[] result = new byte[ADDRESS_BYTES];
        for (int i = 0; i < ADDRESS_BYTES; i++) {
            result[i] = value;
        }
        return result;
    }

    private static String leftPad(String value, int length, char pad) {
        if (value.length() >= length) {
            return value;
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = value.length(); i < length; i++) {
            sb.append(pad);
        }
        sb.append(value);
        return sb.toString();
    }
}
