package com.telecom.storage;

import com.telecom.config.StorageProperties;
import com.telecom.exception.FileTooLargeException;
import com.telecom.exception.InvalidFileException;
import com.telecom.exception.StorageException;
import com.telecom.support.TestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileSystemStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileSystemStorageService(new StorageProperties(
                tempDir.toString(), Set.of("application/pdf"), DataSize.ofMegabytes(1), 5));
    }

    @Test
    void storesBytesAndComputesSizeAndChecksum() throws Exception {
        byte[] data = TestFiles.pdf(200_000);   // larger than the internal buffer: several read loops

        StoredFile stored = storage.store(new ByteArrayInputStream(data), ".pdf", 1_000_000);

        assertThat(stored.key()).endsWith(".pdf");
        assertThat(stored.sizeBytes()).isEqualTo(data.length);
        assertThat(stored.sha256()).isEqualTo(sha256(data));
        assertThat(Files.readAllBytes(tempDir.resolve(stored.key()))).isEqualTo(data);
    }

    @Test
    void generatesUniqueKeysForIdenticalContent() {
        byte[] data = TestFiles.pdf(100);

        StoredFile a = storage.store(new ByteArrayInputStream(data), ".pdf", 1000);
        StoredFile b = storage.store(new ByteArrayInputStream(data), ".pdf", 1000);

        assertThat(a.key()).isNotEqualTo(b.key());
        assertThat(a.sha256()).isEqualTo(b.sha256());
    }

    @Test
    void abortsMidStreamWhenLimitExceededAndLeavesNoFiles() throws IOException {
        byte[] data = TestFiles.pdf(5_000);

        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(data), ".pdf", 4_999))
                .isInstanceOf(FileTooLargeException.class);

        assertThat(filesIn(tempDir)).isZero();
    }

    @Test
    void acceptsFileExactlyAtLimit() {
        byte[] data = TestFiles.pdf(5_000);

        StoredFile stored = storage.store(new ByteArrayInputStream(data), ".pdf", 5_000);

        assertThat(stored.sizeBytes()).isEqualTo(5_000);
    }

    @Test
    void rejectsEmptyInputAndLeavesNoFiles() throws IOException {
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(new byte[0]), ".pdf", 100))
                .isInstanceOf(InvalidFileException.class);

        assertThat(filesIn(tempDir)).isZero();
    }

    @Test
    void readFailureIsWrappedAndPartialFileRemoved() throws IOException {
        InputStream failing = new InputStream() {
            private int count;

            @Override
            public int read() throws IOException {
                if (count++ > 10) {
                    throw new IOException("connection reset");
                }
                return 'x';
            }
        };

        assertThatThrownBy(() -> storage.store(failing, ".pdf", 1000))
                .isInstanceOf(StorageException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThat(filesIn(tempDir)).isZero();
    }

    @Test
    void loadsStoredFileAsResource() throws IOException {
        byte[] data = TestFiles.png(300);
        StoredFile stored = storage.store(new ByteArrayInputStream(data), ".png", 1000);

        Resource resource = storage.load(stored.key());

        assertThat(resource.getContentAsByteArray()).isEqualTo(data);
    }

    @Test
    void loadingMissingFileFails() {
        assertThatThrownBy(() -> storage.load("does-not-exist.pdf")).isInstanceOf(StorageException.class);
    }

    @Test
    void deleteRemovesFileAndIsIdempotent() {
        StoredFile stored = storage.store(new ByteArrayInputStream(TestFiles.pdf(10)), ".pdf", 1000);

        storage.delete(stored.key());
        storage.delete(stored.key());

        assertThat(tempDir.resolve(stored.key())).doesNotExist();
    }

    @Test
    void rejectsPathTraversalKeys() {
        assertThatThrownBy(() -> storage.load("../outside.pdf")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.delete("../../etc/passwd")).isInstanceOf(StorageException.class);
    }

    private static long filesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
