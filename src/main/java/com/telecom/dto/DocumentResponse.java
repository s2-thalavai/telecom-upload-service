package com.telecom.dto;

import com.telecom.entity.CustomerDocument;
import com.telecom.entity.DocumentType;

import java.time.Instant;

public record DocumentResponse(Long id, Long customerId, DocumentType type, String originalFilename,
                               String contentType, long sizeBytes, String sha256, String description,
                               Instant uploadedAt, String downloadUrl) {

    public static DocumentResponse from(CustomerDocument d) {
        Long customerId = d.getCustomer().getId();   // lazy proxy: id access does not hit the DB
        return new DocumentResponse(d.getId(), customerId, d.getDocumentType(), d.getOriginalFilename(),
                d.getContentType(), d.getSizeBytes(), d.getSha256(), d.getDescription(), d.getUploadedAt(),
                "/api/v1/customers/%d/documents/%d".formatted(customerId, d.getId()));
    }
}
