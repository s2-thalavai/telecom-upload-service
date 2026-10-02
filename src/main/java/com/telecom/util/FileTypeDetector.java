package com.telecom.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Detects the real file type from its leading "magic bytes" (the client's Content-Type is not trusted).
 * For broader coverage swap in Apache Tika.
 */
public final class FileTypeDetector {

    static final int HEADER_LENGTH = 8;
    private static final Map<String, byte[]> SIGNATURES = new LinkedHashMap<>();

    static {
        SIGNATURES.put("application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});                 // %PDF-
        SIGNATURES.put("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        SIGNATURES.put("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private FileTypeDetector() {
    }

    /** Peeks at the header without consuming it (mark/reset), so the same stream can then be stored. */
    public static Optional<String> detect(BufferedInputStream in) {
        try {
            in.mark(HEADER_LENGTH);
            byte[] head = in.readNBytes(HEADER_LENGTH);
            in.reset();
            return SIGNATURES.entrySet().stream()
                    .filter(e -> startsWith(head, e.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file header", e);
        }
    }

    public static String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> ".bin";
        };
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        return data.length >= signature.length
                && Arrays.equals(data, 0, signature.length, signature, 0, signature.length);
    }
}
