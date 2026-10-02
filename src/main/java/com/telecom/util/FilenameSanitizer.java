package com.telecom.util;

import org.springframework.util.StringUtils;

/** Makes a client-supplied filename safe for display and Content-Disposition. Never used as a path. */
public final class FilenameSanitizer {

    static final int MAX_LENGTH = 255;
    static final String FALLBACK = "upload";

    private FilenameSanitizer() {
    }

    public static String sanitize(String original) {
        String path = StringUtils.cleanPath(original == null ? "" : original);   // "\" -> "/", collapses ".."
        String name = StringUtils.getFilename(path);                              // drop any directory part
        String cleaned = (name == null ? "" : name)
                .replaceAll("[\\p{Cntrl}\"\\\\/:*?<>|]", "_")
                .strip();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return FALLBACK;
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(cleaned.length() - MAX_LENGTH) : cleaned;
    }
}
