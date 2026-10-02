package com.telecom.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Byte payloads with real magic numbers, so type detection is exercised for real. */
public final class TestFiles {

    private TestFiles() {
    }

    public static byte[] pdf(int size) {
        return withHeader("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), size);
    }

    public static byte[] pdf(String uniqueText) {
        return ("%PDF-1.7\n" + uniqueText + "\n%%EOF").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] png(int size) {
        return withHeader(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, size);
    }

    public static byte[] jpeg(int size) {
        return withHeader(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, size);
    }

    /** Windows executable header ("MZ") - must be rejected even if named .pdf. */
    public static byte[] exe(int size) {
        return withHeader(new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00}, size);
    }

    private static byte[] withHeader(byte[] header, int size) {
        byte[] data = Arrays.copyOf(header, Math.max(size, header.length));
        for (int i = header.length; i < data.length; i++) {
            data[i] = (byte) ('a' + (i % 26));
        }
        return data;
    }
}
