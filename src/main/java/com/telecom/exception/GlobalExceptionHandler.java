package com.telecom.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail invalidFile(InvalidFileException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid File", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ProblemDetail unsupportedType(UnsupportedFileTypeException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported File Type", ex.getMessage());
    }

    /** Thrown by the multipart resolver when spring.servlet.multipart.* limits are exceeded. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail multipartTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File Too Large", "Upload exceeds the allowed size");
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ProblemDetail streamTooLarge(FileTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File Too Large", ex.getMessage());
    }

    /** Other multipart problems, e.g. "Current request is not a multipart request". */
    @ExceptionHandler(MultipartException.class)
    public ProblemDetail badMultipart(MultipartException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed Multipart Request", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail duplicate(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate", ex.getMessage());
    }

    /** @RequestBody / @RequestPart (MethodArgumentNotValidException) and @ModelAttribute validation. */
    @ExceptionHandler(BindException.class)
    public ProblemDetail validation(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid Request", "Validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(StorageException.class)
    public ProblemDetail storage(StorageException ex) {
        log.error("Storage failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Storage Error", "The file could not be stored or read");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
