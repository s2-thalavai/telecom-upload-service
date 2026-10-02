package com.telecom.exception;

/** HTTP 404 - entity not found. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
