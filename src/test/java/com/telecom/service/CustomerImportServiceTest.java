package com.telecom.service;

import com.telecom.config.ImportProperties;
import com.telecom.dto.ImportResult;
import com.telecom.dto.RowError;
import com.telecom.entity.Customer;
import com.telecom.exception.InvalidFileException;
import com.telecom.exception.UnsupportedFileTypeException;
import com.telecom.repository.CustomerRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uses a real Bean Validation validator; repository and transactions are mocked. */
@ExtendWith(MockitoExtension.class)
class CustomerImportServiceTest {

    private static final String HEADER = "name,email,msisdn,planType\n";
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    @Mock
    private CustomerRepository customers;

    private CustomerImportService service;

    @BeforeEach
    void setUp() {
        Validator validator = VALIDATOR_FACTORY.getValidator();
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new CustomerImportService(customers, validator, tx, new ImportProperties(2, 3));
    }

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void importsValidRowsInBatches() {
        String csv = HEADER
                + "A One,a1@example.com,9000000001,PREPAID\n"
                + "A Two,a2@example.com,9000000002,postpaid\n"
                + "\"Rao, Venkat\",a3@example.com,9000000003,PREPAID\n"
                + "\n"
                + "A Four,a4@example.com,9000000004,POSTPAID\n"
                + "A Five,a5@example.com,9000000005,PREPAID\n";

        ImportResult result = service.importCsv(csv("customers.csv", csv));

        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.imported()).isEqualTo(5);
        assertThat(result.failed()).isZero();
        verify(customers, times(3)).saveAll(anyList());   // batch size 2 -> 2 + 2 + 1
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesQuotedNames() {
        service.importCsv(csv("c.csv", HEADER + "\"Rao, Venkat\",v@example.com,9000000003,PREPAID\n"));

        ArgumentCaptor<List<Customer>> captor = ArgumentCaptor.forClass(List.class);
        verify(customers).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().extracting(Customer::getName).isEqualTo("Rao, Venkat");
    }

    @Test
    void reportsInvalidRowsWithLineNumbers() {
        String csv = HEADER
                + "Good,good@example.com,9000000001,PREPAID\n"      // line 2
                + "Bad Email,not-an-email,9000000002,PREPAID\n"     // line 3
                + "Bad Plan,bp@example.com,9000000003,GOLD\n"       // line 4
                + "Too,Few,Columns\n"                               // line 5
                + "Bad Phone,bph@example.com,12345,PREPAID\n";      // line 6

        ImportResult result = service.importCsv(csv("c.csv", csv));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(4);
        assertThat(result.errors()).extracting(RowError::line).containsExactly(3, 4, 5);   // capped at 3
        assertThat(result.errors().get(0).message()).contains("email");
        assertThat(result.errors().get(1).message()).contains("planType");
        assertThat(result.errors().get(2).message()).contains("Expected 4 columns");
    }

    @Test
    void rejectsDuplicatesWithinFileAndAgainstDatabase() {
        // answer-based stub: strict stubs would flag calls with other msisdns
        when(customers.existsByMsisdn(anyString())).thenAnswer(inv -> "9000000009".equals(inv.getArgument(0)));
        String csv = HEADER
                + "One,dup@example.com,9000000001,PREPAID\n"
                + "Two,DUP@example.com,9000000002,PREPAID\n"     // same email, different case
                + "Three,three@example.com,9000000001,PREPAID\n" // same msisdn as row 1
                + "Four,four@example.com,9000000009,PREPAID\n";  // msisdn already in DB

        ImportResult result = service.importCsv(csv("c.csv", csv));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(3);
        assertThat(result.errors()).extracting(RowError::message)
                .anySatisfy(m -> assertThat(m).startsWith("Duplicate email"))
                .anySatisfy(m -> assertThat(m).startsWith("Duplicate mobile"));
    }

    @Test
    void acceptsHeaderWithUtf8Bom() {
        ImportResult result = service.importCsv(
                csv("excel.csv", "\uFEFF" + HEADER + "Bom,bom@example.com,9000000001,PREPAID\n"));

        assertThat(result.imported()).isEqualTo(1);
    }

    @Test
    void rejectsWrongHeader() {
        assertThatThrownBy(() -> service.importCsv(csv("c.csv", "email,name\nx,y\n")))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("First line must be");
        verify(customers, never()).saveAll(anyList());
    }

    @Test
    void rejectsNonCsvFileName() {
        assertThatThrownBy(() -> service.importCsv(csv("customers.xlsx", HEADER)))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.importCsv(csv("c.csv", "")))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void headerOnlyImportsNothing() {
        ImportResult result = service.importCsv(csv("c.csv", HEADER));

        assertThat(result.totalRows()).isZero();
        verify(customers, never()).saveAll(anyList());
    }

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
