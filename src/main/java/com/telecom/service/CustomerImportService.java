package com.telecom.service;

import com.telecom.config.ImportProperties;
import com.telecom.dto.CustomerRequest;
import com.telecom.dto.ImportResult;
import com.telecom.dto.RowError;
import com.telecom.entity.Customer;
import com.telecom.entity.PlanType;
import com.telecom.exception.InvalidFileException;
import com.telecom.exception.StorageException;
import com.telecom.exception.UnsupportedFileTypeException;
import com.telecom.repository.CustomerRepository;
import com.telecom.util.CsvLineParser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Streams a CSV line by line, validates each row, commits in batches, reports rejected rows. */
@Service
public class CustomerImportService {

    static final String EXPECTED_HEADER = "name,email,msisdn,planType";

    private final CustomerRepository customers;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;
    private final ImportProperties properties;

    public CustomerImportService(CustomerRepository customers, Validator validator,
                                 TransactionTemplate transactionTemplate, ImportProperties properties) {
        this.customers = customers;
        this.validator = validator;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    public ImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("A non-empty CSV file is required");
        }
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new UnsupportedFileTypeException("Upload a .csv file");
        }

        List<RowError> errors = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenMsisdns = new HashSet<>();
        List<Customer> batch = new ArrayList<>(properties.batchSize());
        int total = 0;
        int imported = 0;
        int failed = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null || !EXPECTED_HEADER.equalsIgnoreCase(stripBom(header).strip())) {
                throw new InvalidFileException("First line must be: " + EXPECTED_HEADER);
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {     // one line in memory at a time
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                total++;
                try {
                    batch.add(toCustomer(line, seenEmails, seenMsisdns));
                } catch (RowRejectedException e) {
                    failed++;
                    if (errors.size() < properties.maxReportedErrors()) {
                        errors.add(new RowError(lineNumber, e.getMessage()));
                    }
                }
                if (batch.size() >= properties.batchSize()) {
                    imported += flush(batch);
                }
            }
            imported += flush(batch);
        } catch (IOException e) {
            throw new StorageException("Could not read the CSV file", e);
        }
        return new ImportResult(total, imported, failed, errors);
    }

    /** Each batch commits independently: one bad batch does not undo earlier ones. */
    private int flush(List<Customer> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        int size = batch.size();
        List<Customer> toSave = List.copyOf(batch);
        transactionTemplate.executeWithoutResult(status -> customers.saveAll(toSave));
        batch.clear();
        return size;
    }

    private Customer toCustomer(String line, Set<String> seenEmails, Set<String> seenMsisdns) {
        List<String> cols = CsvLineParser.parse(line);
        if (cols.size() != 4) {
            throw new RowRejectedException("Expected 4 columns but found " + cols.size());
        }

        PlanType plan;
        try {
            plan = PlanType.valueOf(cols.get(3).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RowRejectedException("planType must be PREPAID or POSTPAID");
        }

        CustomerRequest request = new CustomerRequest(
                cols.get(0).strip(), cols.get(1).strip(), cols.get(2).strip(), plan);
        Set<ConstraintViolation<CustomerRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new RowRejectedException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }

        String email = request.email().toLowerCase(Locale.ROOT);
        if (seenEmails.contains(email) || customers.existsByEmailIgnoreCase(email)) {
            throw new RowRejectedException("Duplicate email: " + request.email());
        }
        if (seenMsisdns.contains(request.msisdn()) || customers.existsByMsisdn(request.msisdn())) {
            throw new RowRejectedException("Duplicate mobile number: " + request.msisdn());
        }
        seenEmails.add(email);
        seenMsisdns.add(request.msisdn());

        return new Customer(request.name(), request.email(), request.msisdn(), request.planType());
    }

    private static String stripBom(String s) {
        return s.startsWith("\uFEFF") ? s.substring(1) : s;    // Excel adds a UTF-8 BOM
    }

    private static final class RowRejectedException extends RuntimeException {
        RowRejectedException(String message) {
            super(message);
        }
    }
}
