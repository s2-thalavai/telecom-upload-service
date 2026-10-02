package com.telecom.controller;

import com.telecom.dto.DocumentDownload;
import com.telecom.dto.DocumentResponse;
import com.telecom.entity.DocumentType;
import com.telecom.exception.FileTooLargeException;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.exception.UnsupportedFileTypeException;
import com.telecom.service.CustomerDocumentService;
import com.telecom.support.TestFiles;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer slice: request binding, validation, status codes and headers. Service is mocked. */
@WebMvcTest(CustomerDocumentController.class)
class CustomerDocumentControllerTest {

    private static final String BASE = "/api/v1/customers/1/documents";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerDocumentService service;

    // ------------------------------------------------------------ single upload

    @Test
    void uploadBindsMultipartAndReturns201WithLocation() throws Exception {
        byte[] bytes = TestFiles.pdf(100);
        when(service.upload(eq(1L), eq(DocumentType.ID_PROOF), eq("Govt ID"), any())).thenReturn(doc(10));

        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "id.pdf", "application/pdf", bytes))
                        .param("type", "ID_PROOF")
                        .param("description", "Govt ID"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(service).upload(eq(1L), eq(DocumentType.ID_PROOF), eq("Govt ID"), file.capture());
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("id.pdf");
        assertThat(file.getValue().getBytes()).isEqualTo(bytes);
    }

    @Test
    void missingFilePartIs400() throws Exception {
        mvc.perform(multipart(BASE).param("type", "ID_PROOF"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unknownDocumentTypeIs400() throws Exception {
        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "id.pdf", "application/pdf", TestFiles.pdf(10)))
                        .param("type", "PASSPORT_SELFIE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void tooLongDescriptionIs400() throws Exception {
        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "id.pdf", "application/pdf", TestFiles.pdf(10)))
                        .param("type", "ID_PROOF")
                        .param("description", "x".repeat(256)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void nonMultipartRequestIs415() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unsupportedFileTypeMapsTo415ProblemDetail() throws Exception {
        when(service.upload(eq(1L), eq(DocumentType.OTHER), isNull(), any()))
                .thenThrow(new UnsupportedFileTypeException("Only PDF, JPEG and PNG files are accepted"));

        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", TestFiles.exe(10)))
                        .param("type", "OTHER"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Unsupported File Type"));
    }

    @Test
    void multipartSizeLimitMapsTo413() throws Exception {
        when(service.upload(eq(1L), eq(DocumentType.OTHER), isNull(), any()))
                .thenThrow(new MaxUploadSizeExceededException(1024));

        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", TestFiles.pdf(10)))
                        .param("type", "OTHER"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("File Too Large"));
    }

    // ------------------------------------------------------------ batch

    @Test
    void batchBindsJsonMetadataPartAndFileList() throws Exception {
        when(service.uploadAll(eq(1L), eq(DocumentType.ADDRESS_PROOF), eq("bills"), anyList()))
                .thenReturn(List.of(doc(1), doc(2)));

        mvc.perform(multipart(BASE + "/batch")
                        .file(new MockMultipartFile("metadata", "", "application/json",
                                "{\"type\":\"ADDRESS_PROOF\",\"description\":\"bills\"}".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("files", "jan.pdf", "application/pdf", TestFiles.pdf("jan")))
                        .file(new MockMultipartFile("files", "feb.pdf", "application/pdf", TestFiles.pdf("feb"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(service).uploadAll(eq(1L), eq(DocumentType.ADDRESS_PROOF), eq("bills"), files.capture());
        assertThat(files.getValue()).extracting(MultipartFile::getOriginalFilename)
                .containsExactly("jan.pdf", "feb.pdf");
    }

    @Test
    void batchWithInvalidMetadataIs400WithFieldErrors() throws Exception {
        mvc.perform(multipart(BASE + "/batch")
                        .file(new MockMultipartFile("metadata", "", "application/json", "{}".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("files", "a.pdf", "application/pdf", TestFiles.pdf(10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());
        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------ streaming

    @Test
    void streamPassesRawBodyAndHeadersToService() throws Exception {
        byte[] bytes = TestFiles.pdf(5_000);
        when(service.uploadStream(eq(1L), eq(DocumentType.CONTRACT), eq("contract.pdf"), any(), any()))
                .thenAnswer(inv -> {
                    InputStream body = inv.getArgument(4);
                    assertThat(body.readAllBytes()).isEqualTo(bytes);
                    return doc(11);
                });

        mvc.perform(post(BASE + "/stream")
                        .param("type", "CONTRACT")
                        .header("X-Filename", "contract.pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/11"));
    }

    @Test
    void streamUsesDefaultFilenameWhenHeaderMissing() throws Exception {
        when(service.uploadStream(eq(1L), eq(DocumentType.OTHER), eq("upload"), any(), any())).thenReturn(doc(12));

        mvc.perform(post(BASE + "/stream").param("type", "OTHER")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(TestFiles.pdf(10)))
                .andExpect(status().isCreated());
    }

    @Test
    void streamTooLargeIs413() throws Exception {
        when(service.uploadStream(eq(1L), eq(DocumentType.OTHER), any(), any(), any()))
                .thenThrow(new FileTooLargeException("File exceeds the limit"));

        mvc.perform(post(BASE + "/stream").param("type", "OTHER")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(TestFiles.pdf(10)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void streamRejectsUnlistedContentType() throws Exception {
        mvc.perform(post(BASE + "/stream").param("type", "OTHER")
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------ list / download / delete

    @Test
    void listReturnsJsonArray() throws Exception {
        when(service.list(1L)).thenReturn(List.of(doc(1), doc(2)));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].downloadUrl").value(BASE + "/1"));
    }

    @Test
    void downloadStreamsBytesWithHeaders() throws Exception {
        byte[] bytes = TestFiles.pdf(256);
        when(service.download(1L, 10L)).thenReturn(new DocumentDownload(
                new ByteArrayResource(bytes), "id.pdf", "application/pdf", bytes.length, "abc123"));

        mvc.perform(get(BASE + "/10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("ETag", "\"abc123\""))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("id.pdf")))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void downloadUnknownDocumentIs404() throws Exception {
        when(service.download(1L, 99L)).thenThrow(new ResourceNotFoundException("Document 99 not found"));

        mvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document 99 not found"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete(BASE + "/10")).andExpect(status().isNoContent());
        verify(service).delete(1L, 10L);
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        doThrow(new ResourceNotFoundException("nope")).when(service).delete(1L, 98L);

        mvc.perform(delete(BASE + "/98")).andExpect(status().isNotFound());
    }

    private static DocumentResponse doc(long id) {
        return new DocumentResponse(id, 1L, DocumentType.ID_PROOF, "id.pdf", "application/pdf", 120,
                "abc123", null, Instant.parse("2026-10-01T10:00:00Z"), BASE + "/" + id);
    }
}
