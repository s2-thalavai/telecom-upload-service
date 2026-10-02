-- Operational queries. Run: make db-reports

-- Documents per type
SELECT document_type, COUNT(*) AS documents, ROUND(SUM(size_bytes) / 1024 / 1024, 2) AS total_mb
FROM customer_documents
GROUP BY document_type;

-- Customers missing KYC (no ID_PROOF)
SELECT c.id, c.name, c.msisdn
FROM customers c
LEFT JOIN customer_documents d ON d.customer_id = c.id AND d.document_type = 'ID_PROOF'
WHERE d.id IS NULL;

-- Largest documents
SELECT id, customer_id, original_filename, content_type, ROUND(size_bytes / 1024 / 1024, 2) AS size_mb
FROM customer_documents
ORDER BY size_bytes DESC
LIMIT 10;

-- Flyway history
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
