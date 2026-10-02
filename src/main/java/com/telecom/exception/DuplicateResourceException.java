package com.telecom.exception;

/** HTTP 409 - duplicate customer or document. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
