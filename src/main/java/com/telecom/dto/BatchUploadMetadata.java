package com.telecom.dto;

import com.telecom.entity.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JSON part of the batch upload request. */
public record BatchUploadMetadata(@NotNull DocumentType type, @Size(max = 255) String description) {
}
