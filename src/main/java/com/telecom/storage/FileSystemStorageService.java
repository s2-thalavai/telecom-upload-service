package com.telecom.storage;

import com.telecom.config.StorageProperties;
import com.telecom.exception.FileTooLargeException;
import com.telecom.exception.InvalidFileException;
import com.telecom.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileSystemStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemStorageService.class);
    static final int BUFFER_SIZE = 64 * 1024;

    private final Path root;

    public FileSystemStorageService(StorageProperties properties) {
        this.root = Path.of(properties.location()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage root " + root, e);
        }
        log.info("File storage root: {}", root);
    }

    public Path root() {
        return root;
    }

    @Override
    public StoredFile store(InputStream input, String extension, long maxBytes) {
        String key = UUID.randomUUID() + extension;          // never trust client filenames for paths
        Path target = resolve(key);
        Path partial = target.resolveSibling(key + ".part");  // invisible until complete
        MessageDigest digest = newSha256();
        long total = 0;

        try (OutputStream out = Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {                       // abort mid-stream, not after the fact
                    throw new FileTooLargeException("File exceeds the limit of " + maxBytes + " bytes");
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new StorageException("Failed to store file", e);
        } catch (RuntimeException e) {
            deleteQuietly(partial);
            throw e;
        }

        if (total == 0) {
            deleteQuietly(partial);
            throw new InvalidFileException("File is empty");
        }

        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new StorageException("Failed to finalize stored file", e);
        }
        return new StoredFile(key, total, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isReadable(file)) {
            throw new StorageException("Stored file is missing: " + key);
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        deleteQuietly(resolve(key));
    }

    /** Path-traversal guard: the resolved path must stay inside the storage root. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new StorageException("Invalid storage key");
        }
        return path;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete {}", path, e);
        }
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
