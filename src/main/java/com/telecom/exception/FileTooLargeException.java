package com.telecom.exception;

/** HTTP 413 - limit exceeded on the streaming endpoint. */
public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(String message) {
        super(message);
    }
}
