package com.telecom.service;

import com.telecom.dto.CustomerRequest;
import com.telecom.dto.CustomerResponse;
import com.telecom.entity.Customer;
import com.telecom.exception.DuplicateResourceException;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<CustomerResponse> getAll() {
        return repository.findAll().stream().map(CustomerResponse::from).toList();
    }

    public CustomerResponse getById(Long id) {
        return repository.findById(id).map(CustomerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }
        if (repository.existsByMsisdn(request.msisdn())) {
            throw new DuplicateResourceException("Mobile number already registered: " + request.msisdn());
        }
        return CustomerResponse.from(repository.save(
                new Customer(request.name(), request.email(), request.msisdn(), request.planType())));
    }
}
