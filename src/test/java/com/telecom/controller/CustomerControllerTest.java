package com.telecom.controller;

import com.telecom.dto.CustomerRequest;
import com.telecom.dto.CustomerResponse;
import com.telecom.entity.PlanType;
import com.telecom.exception.DuplicateResourceException;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final String VALID = """
            {"name":"Arun","email":"arun@example.com","msisdn":"9876543210","planType":"PREPAID"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerService service;

    @Test
    void createReturns201() throws Exception {
        when(service.create(any(CustomerRequest.class))).thenReturn(new CustomerResponse(
                5L, "Arun", "arun@example.com", "9876543210", PlanType.PREPAID, Instant.now()));

        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/customers/5"))
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"bad\",\"msisdn\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.msisdn").exists())
                .andExpect(jsonPath("$.errors.planType").exists());
        verifyNoInteractions(service);
    }

    @Test
    void duplicateIs409() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateResourceException("Email already registered"));

        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        when(service.getById(9L)).thenThrow(new ResourceNotFoundException("Customer not found with id 9"));

        mvc.perform(get("/api/v1/customers/9")).andExpect(status().isNotFound());
    }
}
