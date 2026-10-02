package com.telecom.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/** Abstraction so the filesystem can be swapped for S3 / Azure Blob without touching services. */
public interface StorageService {

    /** Streams the input to storage, enforcing maxBytes and computing SHA-256 on the fly. */
    StoredFile store(InputStream input, String extension, long maxBytes);

    Resource load(String key);

    void delete(String key);
}
