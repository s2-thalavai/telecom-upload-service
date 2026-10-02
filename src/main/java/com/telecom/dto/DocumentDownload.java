package com.telecom.dto;

import org.springframework.core.io.Resource;

public record DocumentDownload(Resource resource, String filename, String contentType,
                               long sizeBytes, String sha256) {
}
