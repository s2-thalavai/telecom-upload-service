package com.telecom.service;

import com.telecom.dto.CustomerRequest;
import com.telecom.dto.CustomerResponse;
import com.telecom.entity.Customer;
import com.telecom.entity.PlanType;
import com.telecom.exception.DuplicateResourceException;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final CustomerRequest REQUEST =
            new CustomerRequest("Arun", "arun@example.com", "9876543210", PlanType.PREPAID);

    @Mock
    private CustomerRepository repository;

    @InjectMocks
    private CustomerService service;

    @Test
    void createsCustomer() {
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = service.create(REQUEST);

        assertThat(response.email()).isEqualTo("arun@example.com");
        assertThat(response.planType()).isEqualTo(PlanType.PREPAID);
    }

    @Test
    void rejectsDuplicateEmail() {
        when(repository.existsByEmailIgnoreCase("arun@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(REQUEST)).isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsDuplicateMsisdn() {
        when(repository.existsByMsisdn("9876543210")).thenReturn(true);

        assertThatThrownBy(() -> service.create(REQUEST)).isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Mobile");
    }

    @Test
    void getByIdNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
