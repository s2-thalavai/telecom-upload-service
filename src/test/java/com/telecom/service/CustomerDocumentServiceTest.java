package com.telecom.service;

import com.telecom.config.StorageProperties;
import com.telecom.dto.DocumentDownload;
import com.telecom.dto.DocumentResponse;
import com.telecom.entity.Customer;
import com.telecom.entity.CustomerDocument;
import com.telecom.entity.DocumentType;
import com.telecom.entity.PlanType;
import com.telecom.exception.DuplicateResourceException;
import com.telecom.exception.FileTooLargeException;
import com.telecom.exception.InvalidFileException;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.exception.UnsupportedFileTypeException;
import com.telecom.repository.CustomerDocumentRepository;
import com.telecom.repository.CustomerRepository;
import com.telecom.storage.StorageService;
import com.telecom.storage.StoredFile;
import com.telecom.support.TestFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test: repositories and storage are mocked. Transaction synchronization is
 * initialised manually so the rollback / after-commit file clean-up logic can be asserted.
 */
@ExtendWith(MockitoExtension.class)
class CustomerDocumentServiceTest {

    private static final long CUSTOMER_ID = 1L;
    private static final StoredFile STORED = new StoredFile("key-123.pdf", 120, "abc123");

    @Mock
    private CustomerRepository customers;
    @Mock
    private CustomerDocumentRepository documents;
    @Mock
    private StorageService storage;

    private CustomerDocumentService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties("unused",
                Set.of("application/pdf", "image/png", "image/jpeg"), DataSize.ofKilobytes(10), 3);
        service = new CustomerDocumentService(customers, documents, storage, properties);

        customer = new Customer("Arun", "arun@example.com", "9876543210", PlanType.POSTPAID);
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ------------------------------------------------------------ upload

    @Test
    void uploadStoresFileAndPersistsMetadataWithDetectedType() {
        givenCustomerExists();
        when(storage.store(any(InputStream.class), eq(".pdf"), anyLong())).thenReturn(STORED);
        givenSaveAssignsId(10L);
        // client claims "image/png" but the bytes are a PDF: detected type wins
        MultipartFile file = new MockMultipartFile("file", "../id card.pdf", "image/png", TestFiles.pdf(120));

        DocumentResponse response = service.upload(CUSTOMER_ID, DocumentType.ID_PROOF, "Govt ID", file);

        ArgumentCaptor<CustomerDocument> saved = ArgumentCaptor.forClass(CustomerDocument.class);
        verify(documents).save(saved.capture());
        assertThat(saved.getValue().getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getValue().getOriginalFilename()).isEqualTo("id card.pdf");
        assertThat(saved.getValue().getStorageKey()).isEqualTo("key-123.pdf");
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sha256()).isEqualTo("abc123");
        assertThat(response.downloadUrl()).isEqualTo("/api/v1/customers/1/documents/10");
    }

    @Test
    void rejectsEmptyFileBeforeTouchingAnything() {
        MultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(CUSTOMER_ID, DocumentType.OTHER, null, empty))
                .isInstanceOf(InvalidFileException.class);
        verifyNoInteractions(customers, storage, documents);
    }

    @Test
    void rejectsUnknownCustomer() {
        when(customers.findById(99L)).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", TestFiles.pdf(50));

        assertThatThrownBy(() -> service.upload(99L, DocumentType.OTHER, null, file))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsDisguisedExecutableWithoutStoringIt() {
        givenCustomerExists();
        MultipartFile fake = new MockMultipartFile("file", "invoice.pdf", "application/pdf", TestFiles.exe(200));

        assertThatThrownBy(() -> service.upload(CUSTOMER_ID, DocumentType.OTHER, null, fake))
                .isInstanceOf(UnsupportedFileTypeException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void duplicateDocumentFailsAndStoredFileIsDeletedOnRollback() {
        givenCustomerExists();
        when(storage.store(any(InputStream.class), eq(".pdf"), anyLong())).thenReturn(STORED);
        when(documents.existsByCustomerIdAndSha256(CUSTOMER_ID, "abc123")).thenReturn(true);
        MultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", TestFiles.pdf(120));

        assertThatThrownBy(() -> service.upload(CUSTOMER_ID, DocumentType.OTHER, null, file))
                .isInstanceOf(DuplicateResourceException.class);
        verify(documents, never()).save(any());

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).delete("key-123.pdf");
    }

    @Test
    void committedUploadKeepsTheFile() {
        givenCustomerExists();
        when(storage.store(any(InputStream.class), eq(".pdf"), anyLong())).thenReturn(STORED);
        givenSaveAssignsId(11L);

        service.upload(CUSTOMER_ID, DocumentType.OTHER, null,
                new MockMultipartFile("file", "a.pdf", "application/pdf", TestFiles.pdf(120)));

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage, never()).delete(any());
    }

    // ------------------------------------------------------------ batch

    @Test
    void batchRejectsTooManyFiles() {
        List<MultipartFile> files = List.of(pdfPart("1"), pdfPart("2"), pdfPart("3"), pdfPart("4"));

        assertThatThrownBy(() -> service.uploadAll(CUSTOMER_ID, DocumentType.OTHER, null, files))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("At most 3");
    }

    @Test
    void batchRejectsEmptyList() {
        assertThatThrownBy(() -> service.uploadAll(CUSTOMER_ID, DocumentType.OTHER, null, List.of()))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void batchUploadsEveryFile() {
        givenCustomerExists();
        when(storage.store(any(InputStream.class), eq(".pdf"), anyLong()))
                .thenReturn(new StoredFile("k1.pdf", 10, "s1"), new StoredFile("k2.pdf", 10, "s2"));
        givenSaveAssignsId(20L);

        List<DocumentResponse> result =
                service.uploadAll(CUSTOMER_ID, DocumentType.ADDRESS_PROOF, "bills", List.of(pdfPart("1"), pdfPart("2")));

        assertThat(result).hasSize(2).allSatisfy(d -> assertThat(d.type()).isEqualTo(DocumentType.ADDRESS_PROOF));
    }

    // ------------------------------------------------------------ stream

    @Test
    void streamRejectsDeclaredOversizeBeforeReading() {
        InputStream body = new ByteArrayInputStream(TestFiles.pdf(10));

        assertThatThrownBy(() -> service.uploadStream(CUSTOMER_ID, DocumentType.CONTRACT, "c.pdf",
                DataSize.ofKilobytes(11).toBytes(), body))
                .isInstanceOf(FileTooLargeException.class);
        verifyNoInteractions(customers, storage);
    }

    @Test
    void streamWithoutContentLengthIsStoredWithStorageLimit() {
        givenCustomerExists();
        when(storage.store(any(InputStream.class), eq(".png"), eq(DataSize.ofKilobytes(10).toBytes())))
                .thenReturn(new StoredFile("p.png", 300, "ff"));
        givenSaveAssignsId(30L);

        DocumentResponse response = service.uploadStream(CUSTOMER_ID, DocumentType.PHOTO, "me.png", null,
                new ByteArrayInputStream(TestFiles.png(300)));

        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.originalFilename()).isEqualTo("me.png");
    }

    // ------------------------------------------------------------ read / delete

    @Test
    void downloadReturnsResourceAndMetadata() {
        CustomerDocument doc = document(5L);
        when(documents.findByIdAndCustomerId(5L, CUSTOMER_ID)).thenReturn(Optional.of(doc));
        when(storage.load("key-123.pdf")).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        DocumentDownload download = service.download(CUSTOMER_ID, 5L);

        assertThat(download.filename()).isEqualTo("id.pdf");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.sha256()).isEqualTo("abc123");
    }

    @Test
    void downloadOfAnotherCustomersDocumentIsNotFound() {
        when(documents.findByIdAndCustomerId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(2L, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesFileOnlyAfterCommit() {
        CustomerDocument doc = document(5L);
        when(documents.findByIdAndCustomerId(5L, CUSTOMER_ID)).thenReturn(Optional.of(doc));

        service.delete(CUSTOMER_ID, 5L);

        verify(documents).delete(doc);
        verify(storage, never()).delete(any());           // not yet: transaction still open

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete("key-123.pdf");
    }

    @Test
    void listForUnknownCustomerIsNotFound() {
        when(customers.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.list(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------ helpers

    private void givenCustomerExists() {
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    }

    private void givenSaveAssignsId(long id) {
        when(documents.save(any(CustomerDocument.class))).thenAnswer(inv -> {
            CustomerDocument d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", id);
            return d;
        });
    }

    private CustomerDocument document(long id) {
        CustomerDocument doc = new CustomerDocument(customer, DocumentType.ID_PROOF, "id.pdf", "key-123.pdf",
                "application/pdf", 120, "abc123", null);
        ReflectionTestUtils.setField(doc, "id", id);
        return doc;
    }

    private static MultipartFile pdfPart(String unique) {
        return new MockMultipartFile("files", unique + ".pdf", "application/pdf", TestFiles.pdf(unique));
    }

    private static void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status));
    }
}
