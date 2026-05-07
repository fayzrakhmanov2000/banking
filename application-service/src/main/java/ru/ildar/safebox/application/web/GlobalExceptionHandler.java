package ru.ildar.safebox.application.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.ildar.safebox.application.exception.ApplicationNotFoundException;
import ru.ildar.safebox.application.exception.DuplicateEventException;
import ru.ildar.safebox.application.exception.InvalidStatusTransitionException;
import ru.ildar.safebox.application.web.dto.ErrorResponse;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ApplicationNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> conflict(InvalidStatusTransitionException e) {
        return build(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", e.getMessage());
    }

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<ErrorResponse> duplicate(DuplicateEventException e) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_EVENT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", e.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String msg) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(code, msg, MDC.get("traceId"), Instant.now())
        );
    }
}
