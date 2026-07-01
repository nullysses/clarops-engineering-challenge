package com.clara.challenge.watchdog.api;

import com.clara.challenge.watchdog.api.exception.WatchdogConflictException;
import com.clara.challenge.watchdog.api.exception.WatchdogNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  private static final String INVALID_REQUEST = "INVALID_REQUEST";
  private static final String NOT_FOUND = "NOT_FOUND";
  private static final String CONFLICT = "CONFLICT";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception) {
    return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, methodArgumentValidationMessage(exception));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception) {
    return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, constraintViolationMessage(exception));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable() {
    return error(
        HttpStatus.BAD_REQUEST,
        INVALID_REQUEST,
        "Request body is malformed or contains incompatible types");
  }

  @ExceptionHandler(WatchdogNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFound(WatchdogNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(WatchdogConflictException.class)
  public ResponseEntity<ApiErrorResponse> handleConflict(WatchdogConflictException exception) {
    return error(HttpStatus.CONFLICT, CONFLICT, exception.getMessage());
  }

  private ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, Instant.now()));
  }

  private String methodArgumentValidationMessage(MethodArgumentNotValidException exception) {
    return exception.getBindingResult().getAllErrors().stream()
        .min(Comparator.comparing(this::validationSortKey))
        .map(this::defaultMessage)
        .orElse("Request validation failed");
  }

  private String constraintViolationMessage(ConstraintViolationException exception) {
    return exception.getConstraintViolations().stream()
        .min(Comparator.comparing(this::constraintViolationSortKey))
        .map(this::constraintViolationMessage)
        .orElse("Request validation failed");
  }

  private String validationSortKey(ObjectError error) {
    if (error instanceof FieldError fieldError) {
      return error.getObjectName() + ":" + fieldError.getField() + ":" + defaultMessage(error);
    }
    return error.getObjectName() + ":" + defaultMessage(error);
  }

  private String constraintViolationSortKey(ConstraintViolation<?> violation) {
    return violation.getPropertyPath() + ":" + constraintViolationMessage(violation);
  }

  private String defaultMessage(ObjectError error) {
    String message = error.getDefaultMessage();
    return message == null || message.isBlank() ? "Request validation failed" : message;
  }

  private String constraintViolationMessage(ConstraintViolation<?> violation) {
    String message = violation.getMessage();
    return message == null || message.isBlank() ? "Request validation failed" : message;
  }
}
