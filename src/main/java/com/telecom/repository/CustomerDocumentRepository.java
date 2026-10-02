package com.telecom.repository;

import com.telecom.entity.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, Long> {

    List<CustomerDocument> findByCustomerIdOrderByUploadedAtDesc(Long customerId);

    Optional<CustomerDocument> findByIdAndCustomerId(Long id, Long customerId);

    boolean existsByCustomerIdAndSha256(Long customerId, String sha256);
}
