package com.telecom.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Metadata only: the bytes live in StorageService, referenced by storageKey. */
@Entity
@Table(name = "customer_documents",
       indexes = @Index(name = "idx_doc_customer", columnList = "customer_id"),
       uniqueConstraints = @UniqueConstraint(name = "uk_doc_customer_sha",
                                             columnNames = {"customer_id", "sha256"}))
public class CustomerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    /** Sanitized client filename, for display/download only. Never used as a path. */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** Server-generated storage name (UUID + extension). */
    @Column(name = "storage_key", nullable = false, unique = true, length = 100)
    private String storageKey;

    /** Detected from magic bytes, not trusted from the client. */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(length = 255)
    private String description;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected CustomerDocument() {
        // JPA
    }

    public CustomerDocument(Customer customer, DocumentType documentType, String originalFilename,
                            String storageKey, String contentType, long sizeBytes,
                            String sha256, String description) {
        this.customer = customer;
        this.documentType = documentType;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        this.uploadedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public DocumentType getDocumentType() { return documentType; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getDescription() { return description; }
    public Instant getUploadedAt() { return uploadedAt; }
}
