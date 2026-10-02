package com.telecom.storage;

public record StoredFile(String key, long sizeBytes, String sha256) {
}
