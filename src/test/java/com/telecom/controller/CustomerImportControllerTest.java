package com.telecom.controller;

import com.telecom.dto.ImportResult;
import com.telecom.dto.RowError;
import com.telecom.exception.InvalidFileException;
import com.telecom.service.CustomerImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerImportController.class)
class CustomerImportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerImportService service;

    @Test
    void returnsImportReport() throws Exception {
        when(service.importCsv(any())).thenReturn(
                new ImportResult(3, 2, 1, List.of(new RowError(3, "email must be a well-formed email address"))));

        mvc.perform(multipart("/api/v1/customers/import")
                        .file(new MockMultipartFile("file", "c.csv", "text/csv", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.errors[0].line").value(3));
    }

    @Test
    void invalidCsvIs400() throws Exception {
        when(service.importCsv(any())).thenThrow(new InvalidFileException("First line must be: name,email,msisdn,planType"));

        mvc.perform(multipart("/api/v1/customers/import")
                        .file(new MockMultipartFile("file", "c.csv", "text/csv", "bad".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid File"));
    }
}
