package com.telecom.service;

import com.telecom.config.StorageProperties;
import com.telecom.dto.DocumentDownload;
import com.telecom.dto.DocumentResponse;
import com.telecom.entity.Customer;
import com.telecom.entity.CustomerDocument;
import com.telecom.entity.DocumentType;
import com.telecom.exception.DuplicateResourceException;
import com.telecom.exception.FileTooLargeException;
import com.telecom.exception.InvalidFileException;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.exception.StorageException;
import com.telecom.exception.UnsupportedFileTypeException;
import com.telecom.repository.CustomerDocumentRepository;
import com.telecom.repository.CustomerRepository;
import com.telecom.storage.StorageService;
import com.telecom.storage.StoredFile;
import com.telecom.util.FileTypeDetector;
import com.telecom.util.FilenameSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Coordinates bytes (StorageService) and metadata (JPA).
 * Transaction synchronizations keep them consistent:
 * rollback -> newly stored file deleted; delete -> file removed only after commit.
 */
@Service
public class CustomerDocumentService {

    private final CustomerRepository customers;
    private final CustomerDocumentRepository documents;
    private final StorageService storage;
    private final StorageProperties properties;

    public CustomerDocumentService(CustomerRepository customers, CustomerDocumentRepository documents,
                                   StorageService storage, StorageProperties properties) {
        this.customers = customers;
        this.documents = documents;
        this.storage = storage;
        this.properties = properties;
    }

    /** Multipart upload: the part is already size-limited by spring.servlet.multipart.*. */
    @Transactional
    public DocumentResponse upload(Long customerId, DocumentType type, String description, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("A non-empty file is required");
        }
        try (InputStream in = file.getInputStream()) {          // stream, never file.getBytes()
            return storeAndRecord(customerId, type, description, file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new StorageException("Could not read the uploaded file", e);
        }
    }

    /** Several files in one request; all-or-nothing thanks to the surrounding transaction. */
    @Transactional
    public List<DocumentResponse> uploadAll(Long customerId, DocumentType type, String description,
                                            List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidFileException("At least one file is required");
        }
        if (files.size() > properties.maxFilesPerBatch()) {
            throw new InvalidFileException("At most " + properties.maxFilesPerBatch() + " files per request");
        }
        return files.stream().map(f -> upload(customerId, type, description, f)).toList();
    }

    /** Raw streaming upload: the body goes straight from the socket to storage. */
    @Transactional
    public DocumentResponse uploadStream(Long customerId, DocumentType type, String filename,
                                         Long contentLength, InputStream body) {
        long max = properties.maxStreamSize().toBytes();
        if (contentLength != null && contentLength > max) {     // fail fast before reading
            throw new FileTooLargeException("File exceeds the limit of " + max + " bytes");
        }
        return storeAndRecord(customerId, type, null, filename, body);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(Long customerId) {
        ensureCustomerExists(customerId);
        return documents.findByCustomerIdOrderByUploadedAtDesc(customerId).stream()
                .map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(Long customerId, Long documentId) {
        CustomerDocument doc = findDocument(customerId, documentId);
        return new DocumentDownload(storage.load(doc.getStorageKey()), doc.getOriginalFilename(),
                doc.getContentType(), doc.getSizeBytes(), doc.getSha256());
    }

    @Transactional
    public void delete(Long customerId, Long documentId) {
        CustomerDocument doc = findDocument(customerId, documentId);
        String key = doc.getStorageKey();
        documents.delete(doc);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    // ------------------------------------------------------------------

    private DocumentResponse storeAndRecord(Long customerId, DocumentType type, String description,
                                            String originalFilename, InputStream input) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + customerId));

        BufferedInputStream buffered = new BufferedInputStream(input);
        String mediaType = FileTypeDetector.detect(buffered)
                .filter(properties.allowedContentTypes()::contains)
                .orElseThrow(() -> new UnsupportedFileTypeException(
                        "Only " + properties.allowedContentTypes() + " files are accepted"));

        StoredFile stored = storage.store(buffered, FileTypeDetector.extensionFor(mediaType),
                properties.maxStreamSize().toBytes());
        deleteFileUnlessCommitted(stored.key());       // compensation if anything below fails

        if (documents.existsByCustomerIdAndSha256(customerId, stored.sha256())) {
            throw new DuplicateResourceException("This document was already uploaded for the customer");
        }

        CustomerDocument saved = documents.save(new CustomerDocument(customer, type,
                FilenameSanitizer.sanitize(originalFilename), stored.key(), mediaType,
                stored.sizeBytes(), stored.sha256(), description));
        return DocumentResponse.from(saved);
    }

    private void deleteFileUnlessCommitted(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
    }

    private CustomerDocument findDocument(Long customerId, Long documentId) {
        return documents.findByIdAndCustomerId(documentId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document " + documentId + " not found for customer " + customerId));
    }

    private void ensureCustomerExists(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found with id " + customerId);
        }
    }
}
