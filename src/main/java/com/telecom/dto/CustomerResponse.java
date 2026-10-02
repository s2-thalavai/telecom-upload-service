package com.telecom.dto;

import com.telecom.entity.Customer;
import com.telecom.entity.PlanType;

import java.time.Instant;

public record CustomerResponse(Long id, String name, String email, String msisdn,
                               PlanType planType, Instant createdAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getMsisdn(),
                c.getPlanType(), c.getCreatedAt());
    }
}
