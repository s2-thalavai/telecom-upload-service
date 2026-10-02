package com.telecom.controller;

import com.telecom.dto.BatchUploadMetadata;
import com.telecom.dto.DocumentDownload;
import com.telecom.dto.DocumentResponse;
import com.telecom.entity.DocumentType;
import com.telecom.service.CustomerDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/documents")
public class CustomerDocumentController {

    private final CustomerDocumentService service;

    public CustomerDocumentController(CustomerDocumentService service) {
        this.service = service;
    }

    /** Single document: multipart fields "file", "type", optional "description". */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable Long customerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") DocumentType type,
            @RequestParam(value = "description", required = false) @Size(max = 255) String description) {
        DocumentResponse doc = service.upload(customerId, type, description, file);
        return ResponseEntity.created(URI.create(doc.downloadUrl())).body(doc);
    }

    /** Several documents: JSON part "metadata" + repeated "files" parts. */
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<DocumentResponse>> uploadBatch(
            @PathVariable Long customerId,
            @RequestPart("metadata") @Valid BatchUploadMetadata metadata,
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.uploadAll(customerId, metadata.type(), metadata.description(), files));
    }

    /** Large file: the raw body is the file; streamed with constant memory. */
    @PostMapping(value = "/stream", consumes = {
            MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.APPLICATION_PDF_VALUE,
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    public ResponseEntity<DocumentResponse> uploadStream(
            @PathVariable Long customerId,
            @RequestParam("type") DocumentType type,
            @RequestHeader(value = "X-Filename", defaultValue = "upload") String filename,
            @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false) Long contentLength,
            InputStream body) {
        DocumentResponse doc = service.uploadStream(customerId, type, filename, contentLength, body);
        return ResponseEntity.created(URI.create(doc.downloadUrl())).body(doc);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DocumentResponse> list(@PathVariable Long customerId) {
        return service.list(customerId);
    }

    /** Streams the file from storage; Spring also serves HTTP Range requests for Resource bodies. */
    @GetMapping("/{documentId}")
    public ResponseEntity<Resource> download(@PathVariable Long customerId, @PathVariable Long documentId) {
        DocumentDownload file = service.download(customerId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .eTag("\"" + file.sha256() + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long customerId, @PathVariable Long documentId) {
        service.delete(customerId, documentId);
    }
}
