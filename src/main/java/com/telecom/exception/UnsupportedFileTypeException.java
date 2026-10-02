package com.telecom.exception;

/** HTTP 415 - content is not an allowed type. */
public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
