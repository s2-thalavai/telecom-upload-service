package com.telecom.exception;

/** HTTP 400 - empty file, bad CSV header, too many files. */
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }
}
