package com.telecom.integration;

import com.telecom.dto.DocumentResponse;
import com.telecom.entity.Customer;
import com.telecom.entity.DocumentType;
import com.telecom.support.TestFiles;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end document upload flows over real HTTP. */
class DocumentUploadIT extends AbstractIntegrationTest {

    private static String docsUrl(Customer c) {
        return "/api/v1/customers/" + c.getId() + "/documents";
    }

    private ResponseEntity<DocumentResponse> upload(Customer c, byte[] bytes, String filename, String type) {
        MultiValueMap<String, Object> parts = parts();
        parts.add("file", filePart(bytes, filename));
        parts.add("type", type);
        return rest.postForEntity(docsUrl(c), multipartRequest(parts), DocumentResponse.class);
    }

    @Test
    void uploadListDownloadDeleteRoundTrip() {
        Customer customer = createCustomer();
        byte[] bytes = TestFiles.pdf(300_000);

        // upload
        ResponseEntity<DocumentResponse> created = upload(customer, bytes, "Aadhaar card.pdf", "ID_PROOF");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DocumentResponse doc = created.getBody();
        assertThat(doc).isNotNull();
        assertThat(created.getHeaders().getLocation()).hasToString(doc.downloadUrl());
        assertThat(doc.contentType()).isEqualTo("application/pdf");
        assertThat(doc.sizeBytes()).isEqualTo(bytes.length);
        assertThat(doc.sha256()).isEqualTo(sha256(bytes));
        assertThat(storedFileCount()).isEqualTo(1);

        // list
        ResponseEntity<DocumentResponse[]> list = rest.getForEntity(docsUrl(customer), DocumentResponse[].class);
        assertThat(list.getBody()).extracting(DocumentResponse::id).containsExactly(doc.id());

        // download: identical bytes and headers
        ResponseEntity<byte[]> download = rest.getForEntity(doc.downloadUrl(), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isEqualTo(bytes);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(download.getHeaders().getETag()).isEqualTo("\"" + sha256(bytes) + "\"");
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("Aadhaar card.pdf");

        // delete: row and file both gone
        ResponseEntity<Void> deleted = rest.exchange(doc.downloadUrl(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(documents.count()).isZero();
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void downloadSupportsRangeRequests() {
        Customer customer = createCustomer();
        byte[] bytes = TestFiles.pdf(1_000);
        DocumentResponse doc = upload(customer, bytes, "a.pdf", "OTHER").getBody();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RANGE, "bytes=0-9");
        ResponseEntity<byte[]> partial = rest.exchange(doc.downloadUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);

        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(partial.getBody()).hasSize(10).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
    }

    @Test
    void disguisedExecutableIsRejectedAndNothingIsStored() {
        Customer customer = createCustomer();

        ResponseEntity<String> response = rest.postForEntity(docsUrl(customer),
                multipartRequest(fileAndType(TestFiles.exe(2_000), "invoice.pdf", "OTHER")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(documents.count()).isZero();
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void duplicateUploadIs409AndRollbackRemovesTheSecondCopy() {
        Customer customer = createCustomer();
        byte[] bytes = TestFiles.pdf("same content");
        assertThat(upload(customer, bytes, "a.pdf", "ID_PROOF").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> again = rest.postForEntity(docsUrl(customer),
                multipartRequest(fileAndType(bytes, "copy.pdf", "ID_PROOF")), String.class);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(documents.count()).isEqualTo(1);
        assertThat(storedFileCount()).isEqualTo(1);       // compensation deleted the second file
    }

    @Test
    void sameFileForDifferentCustomersIsAllowed() {
        byte[] bytes = TestFiles.pdf("shared");

        assertThat(upload(createCustomer(), bytes, "a.pdf", "OTHER").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload(createCustomer(), bytes, "a.pdf", "OTHER").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void multipartFileOverLimitIs413() {
        Customer customer = createCustomer();
        byte[] tooBig = TestFiles.pdf(1_500_000);             // test profile: max-file-size=1MB

        ResponseEntity<String> response = rest.postForEntity(docsUrl(customer),
                multipartRequest(fileAndType(tooBig, "big.pdf", "OTHER")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void unknownCustomerIs404() {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/customers/999999/documents",
                multipartRequest(fileAndType(TestFiles.pdf(100), "a.pdf", "OTHER")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void batchUploadWithJsonMetadataPart() {
        Customer customer = createCustomer();
        MultiValueMap<String, Object> parts = parts();
        parts.add("metadata", jsonPart("{\"type\":\"ADDRESS_PROOF\",\"description\":\"Utility bills\"}"));
        parts.add("files", filePart(TestFiles.pdf("jan"), "jan.pdf"));
        parts.add("files", filePart(TestFiles.png(500), "feb.png"));

        ResponseEntity<DocumentResponse[]> response = rest.postForEntity(docsUrl(customer) + "/batch",
                multipartRequest(parts), DocumentResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(2)
                .allSatisfy(d -> assertThat(d.type()).isEqualTo(DocumentType.ADDRESS_PROOF))
                .extracting(DocumentResponse::contentType)
                .containsExactly("application/pdf", "image/png");
        assertThat(storedFileCount()).isEqualTo(2);
    }

    @Test
    void batchIsAllOrNothing() {
        Customer customer = createCustomer();
        MultiValueMap<String, Object> parts = parts();
        parts.add("metadata", jsonPart("{\"type\":\"OTHER\"}"));
        parts.add("files", filePart(TestFiles.pdf("good"), "good.pdf"));
        parts.add("files", filePart(TestFiles.exe(100), "evil.pdf"));

        ResponseEntity<String> response = rest.postForEntity(docsUrl(customer) + "/batch",
                multipartRequest(parts), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(documents.count()).isZero();
        assertThat(storedFileCount()).isZero();           // the first, valid file was rolled back too
    }

    @Test
    void streamingUploadBypassesMultipartLimit() {
        Customer customer = createCustomer();
        byte[] bytes = TestFiles.pdf(1_500_000);            // > multipart 1MB, < stream limit 2MB
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set("X-Filename", "signed-contract.pdf");

        ResponseEntity<DocumentResponse> response = rest.postForEntity(docsUrl(customer) + "/stream?type=CONTRACT",
                new HttpEntity<>(bytes, headers), DocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().originalFilename()).isEqualTo("signed-contract.pdf");
        assertThat(response.getBody().sha256()).isEqualTo(sha256(bytes));
        assertThat(storedFileCount()).isEqualTo(1);
    }

    @Test
    void streamingUploadOverLimitIs413() {
        Customer customer = createCustomer();
        byte[] tooBig = TestFiles.pdf(2 * 1024 * 1024 + 1);  // stream limit 2MB
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        ResponseEntity<String> response = rest.postForEntity(docsUrl(customer) + "/stream?type=OTHER",
                new HttpEntity<>(tooBig, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(storedFileCount()).isZero();
    }

    private static MultiValueMap<String, Object> fileAndType(byte[] bytes, String filename, String type) {
        MultiValueMap<String, Object> parts = parts();
        parts.add("file", filePart(bytes, filename));
        parts.add("type", type);
        return parts;
    }

    private static HttpEntity<String> jsonPart(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }
}
