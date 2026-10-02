package com.telecom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "telecom.import")
public record ImportProperties(
        @DefaultValue("500") int batchSize,
        @DefaultValue("100") int maxReportedErrors) {
}
