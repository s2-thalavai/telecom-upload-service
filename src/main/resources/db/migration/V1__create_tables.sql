-- MySQL 8.x schema. Keep in sync with com.telecom.entity.*
CREATE TABLE customers (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    msisdn      VARCHAR(15)  NOT NULL,
    plan_type   VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_email  UNIQUE (email),
    CONSTRAINT uk_customers_msisdn UNIQUE (msisdn)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE customer_documents (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id        BIGINT       NOT NULL,
    document_type      VARCHAR(20)  NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(100) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT       NOT NULL,
    sha256             VARCHAR(64)  NOT NULL,
    description        VARCHAR(255) NULL,
    uploaded_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_doc_customer     FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT uk_doc_storage_key  UNIQUE (storage_key),
    CONSTRAINT uk_doc_customer_sha UNIQUE (customer_id, sha256)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_doc_customer ON customer_documents (customer_id);
