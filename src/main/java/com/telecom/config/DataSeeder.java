package com.telecom.config;

import com.telecom.entity.Customer;
import com.telecom.entity.PlanType;
import com.telecom.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Dev-only sample data. Prod is seeded by Flyway V2; tests create their own data. */
@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository repository;

    public DataSeeder(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            repository.save(new Customer("Arun Kumar", "arun@example.com", "9876543210", PlanType.POSTPAID));
            repository.save(new Customer("Priya S", "priya@example.com", "9123456780", PlanType.PREPAID));
        }
    }
}
