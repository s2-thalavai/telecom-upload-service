package com.telecom.integration;

import com.telecom.entity.Customer;
import com.telecom.entity.PlanType;
import com.telecom.repository.CustomerDocumentRepository;
import com.telecom.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Full-stack integration base: real embedded Tomcat (real multipart parsing and size limits),
 * real filesystem storage in a temp directory, H2 database. Profile "test" uses small limits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final Path STORAGE_ROOT = createTempDir();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @DynamicPropertySource
    static void storageLocation(DynamicPropertyRegistry registry) {
        registry.add("telecom.storage.location", STORAGE_ROOT::toString);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected CustomerRepository customers;

    @Autowired
    protected CustomerDocumentRepository documents;

    @BeforeEach
    void resetState() throws IOException {
        documents.deleteAll();
        customers.deleteAll();
        try (Stream<Path> files = Files.list(STORAGE_ROOT)) {
            for (Path p : files.toList()) {
                FileSystemUtils.deleteRecursively(p);
            }
        }
    }

    protected Customer createCustomer() {
        int n = SEQUENCE.incrementAndGet();
        return customers.save(new Customer("IT Customer " + n, "it" + n + "@example.com",
                "9%09d".formatted(n), PlanType.PREPAID));
    }

    protected static long storedFileCount() {
        try (Stream<Path> files = Files.list(STORAGE_ROOT)) {
            return files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A multipart file part with a filename (RestTemplate derives the part Content-Type from it). */
    protected static ByteArrayResource filePart(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    protected static HttpEntity<MultiValueMap<String, Object>> multipartRequest(MultiValueMap<String, Object> parts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(parts, headers);
    }

    protected static MultiValueMap<String, Object> parts() {
        return new LinkedMultiValueMap<>();
    }

    protected static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path createTempDir() {
        try {
            Path dir = Files.createTempDirectory("telecom-it-storage-");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
