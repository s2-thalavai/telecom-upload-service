package com.telecom.integration;

import com.telecom.dto.ImportResult;
import com.telecom.dto.RowError;
import com.telecom.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Bulk CSV onboarding over real HTTP, committed in batches (test profile batch-size=2). */
class CustomerImportIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/customers/import";

    private ResponseEntity<ImportResult> importCsv(String filename, String csv) {
        MultiValueMap<String, Object> parts = parts();
        parts.add("file", filePart(csv.getBytes(StandardCharsets.UTF_8), filename));
        return rest.postForEntity(URL, multipartRequest(parts), ImportResult.class);
    }

    @Test
    void importsValidRowsAndReportsInvalidOnes() {
        String csv = """
                name,email,msisdn,planType
                Arun Kumar,arun.it@example.com,9811111111,POSTPAID
                "Rao, Venkat",venkat.it@example.com,9822222222,PREPAID
                Bad Email,not-an-email,9833333333,PREPAID
                Meena V,meena.it@example.com,9844444444,prepaid
                Dup Email,ARUN.IT@example.com,9855555555,PREPAID
                Ravi T,ravi.it@example.com,9866666666,POSTPAID
                """;

        ResponseEntity<ImportResult> response = importCsv("customers.csv", csv);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportResult result = response.getBody();
        assertThat(result.totalRows()).isEqualTo(6);
        assertThat(result.imported()).isEqualTo(4);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).extracting(RowError::line).containsExactly(4, 6);
        assertThat(customers.count()).isEqualTo(4);
        assertThat(customers.findAll()).extracting(Customer::getName).contains("Rao, Venkat");
    }

    @Test
    void rowsAlreadyInDatabaseAreRejected() {
        createCustomer();   // it<n>@example.com / 9<n padded>
        String existingMsisdn = customers.findAll().get(0).getMsisdn();

        ImportResult result = importCsv("c.csv",
                "name,email,msisdn,planType\nNew Name,new.it@example.com," + existingMsisdn + ",PREPAID\n").getBody();

        assertThat(result.imported()).isZero();
        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).startsWith("Duplicate mobile"));
    }

    @Test
    void wrongHeaderIs400() {
        ResponseEntity<String> response = rest.postForEntity(URL, multipartRequest(
                csvParts("c.csv", "email,name\nx,y\n")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(customers.count()).isZero();
    }

    @Test
    void nonCsvFileIs415() {
        ResponseEntity<String> response = rest.postForEntity(URL, multipartRequest(
                csvParts("customers.xlsx", "name,email,msisdn,planType\n")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private static MultiValueMap<String, Object> csvParts(String filename, String content) {
        MultiValueMap<String, Object> parts = parts();
        parts.add("file", filePart(content.getBytes(StandardCharsets.UTF_8), filename));
        return parts;
    }
}
