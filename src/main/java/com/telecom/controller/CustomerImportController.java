package com.telecom.controller;

import com.telecom.dto.ImportResult;
import com.telecom.service.CustomerImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/customers/import")
public class CustomerImportController {

    private final CustomerImportService service;

    public CustomerImportController(CustomerImportService service) {
        this.service = service;
    }

    /** Bulk onboarding from CSV: name,email,msisdn,planType */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCustomers(@RequestParam("file") MultipartFile file) {
        return service.importCsv(file);
    }
}
