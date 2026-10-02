package com.telecom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.util.Set;

@ConfigurationProperties(prefix = "telecom.storage")
public record StorageProperties(
        @DefaultValue("./data/uploads") String location,
        @DefaultValue({"application/pdf", "image/jpeg", "image/png"}) Set<String> allowedContentTypes,
        @DefaultValue("200MB") DataSize maxStreamSize,
        @DefaultValue("10") int maxFilesPerBatch) {
}
