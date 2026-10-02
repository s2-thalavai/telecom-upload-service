package com.telecom.integration;

import com.telecom.dto.CustomerResponse;
import com.telecom.dto.DocumentResponse;
import com.telecom.support.TestFiles;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the PROD profile against a real MySQL 8.4 container: verifies the Flyway migrations
 * match the JPA mappings (ddl-auto=none) and that an upload works end to end.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class MySqlFlywayIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) throws IOException {
        Path dir = Files.createTempDirectory("telecom-mysql-it-");
        registry.add("telecom.storage.location", dir::toString);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private Flyway flyway;

    @Test
    void migrationsAreApplied() {
        assertThat(Arrays.stream(flyway.info().applied()).map(m -> m.getVersion().getVersion()))
                .containsExactly("1", "2");
    }

    @Test
    void seededCustomerCanUploadAndDownloadADocument() {
        CustomerResponse[] seeded = rest.getForObject("/api/v1/customers", CustomerResponse[].class);
        assertThat(seeded).isNotEmpty();
        long customerId = seeded[0].id();
        byte[] bytes = TestFiles.pdf("mysql round trip");

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "contract.pdf";
            }
        });
        parts.add("type", "CONTRACT");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<DocumentResponse> created = rest.postForEntity(
                "/api/v1/customers/" + customerId + "/documents", new HttpEntity<>(parts, headers),
                DocumentResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rest.getForObject(created.getBody().downloadUrl(), byte[].class)).isEqualTo(bytes);
    }
}
